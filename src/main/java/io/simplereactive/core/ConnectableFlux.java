package io.simplereactive.core;

import io.simplereactive.scheduler.Disposable;
import io.simplereactive.subscription.Subscriptions;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 수동으로 연결을 제어할 수 있는 Hot Publisher.
 *
 * <p>Cold Publisher를 Hot Publisher로 변환합니다.
 * connect()를 호출하기 전까지는 upstream에 구독하지 않으며,
 * connect() 호출 시 모든 구독자에게 동시에 데이터가 발행됩니다.
 *
 * <h2>Cold vs Hot 변환</h2>
 * <pre>
 * Cold Publisher (구독마다 새로 시작)
 *        │
 *    publish()
 *        │
 *        ▼
 * ConnectableFlux (수동 연결)
 *        │
 *    connect()
 *        │
 *        ▼
 * 모든 구독자에게 동시 발행
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // Cold Publisher를 ConnectableFlux로 변환
 * ConnectableFlux<Integer> hot = Flux.range(1, 5).publish();
 *
 * // 여러 구독자 등록 (아직 데이터 안 받음)
 * hot.subscribe(subscriberA);
 * hot.subscribe(subscriberB);
 *
 * // connect() 호출 시 모든 구독자에게 동시 발행
 * hot.connect();
 * }</pre>
 *
 * <h2>autoConnect</h2>
 * <ul>
 *   <li>autoConnect(): 첫 구독자 등록 시 자동 연결</li>
 *   <li>autoConnect(n): n명 구독 시 자동 연결</li>
 * </ul>
 *
 * <p><strong>Warning:</strong> autoConnect(0) 호출 시 즉시 연결됩니다.
 * 구독자가 없는 상태에서 연결하면 데이터가 유실될 수 있습니다.
 *
 * <h2>Backpressure 미지원</h2>
 * <p>ConnectableFlux는 Backpressure를 지원하지 않습니다.
 * upstream에 Long.MAX_VALUE를 요청하고, 모든 데이터를 구독자에게 브로드캐스트합니다.
 *
 * @param <T> 요소 타입
 * @see Flux#publish()
 * @see Flux#share()
 */
public class ConnectableFlux<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final List<ConnectableSubscription<T>> subscriptions = new CopyOnWriteArrayList<>();
    
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicReference<Subscription> upstreamSubscription = new AtomicReference<>();
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    
    // error를 먼저 설정하고 completed를 나중에 설정 (메모리 가시성)
    private volatile Throwable error;

    /**
     * ConnectableFlux를 생성합니다.
     *
     * @param upstream 원본 Cold Publisher
     * @throws NullPointerException upstream이 null인 경우
     */
    public ConnectableFlux(Publisher<T> upstream) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>connect() 호출 전까지는 구독자를 등록만 하고 데이터를 받지 않습니다.
     * 이미 연결된 상태라면 현재 진행 중인 스트림에 합류합니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Rule 1.9: Subscriber must not be null");

        ConnectableSubscription<T> subscription = new ConnectableSubscription<>(subscriber, this);
        
        // 먼저 리스트에 추가
        subscriptions.add(subscription);
        
        // completed 체크 - 이미 완료되었으면 즉시 종료 시그널
        if (completed.get()) {
            subscriptions.remove(subscription);
            subscriber.onSubscribe(Subscriptions.empty());
            Throwable ex = error;
            if (ex != null) {
                subscriber.onError(ex);
            } else {
                subscriber.onComplete();
            }
            return;
        }
        
        // Rule 1.1: onSubscribe 호출
        subscriber.onSubscribe(subscription);
    }

    /**
     * upstream에 연결하여 데이터 발행을 시작합니다.
     *
     * <p>한 번만 호출할 수 있습니다. 중복 호출 시 동일한 Disposable을 반환합니다.
     * 연결 후 모든 구독자에게 데이터가 발행됩니다.
     *
     * @return 연결 해제를 위한 Disposable (dispose 호출로 upstream 취소)
     */
    public Disposable connect() {
        if (connected.compareAndSet(false, true)) {
            upstream.subscribe(new InnerSubscriber());
            Disposable d = new ConnectionDisposable(upstreamSubscription);
            disposable.set(d);
        }
        return disposable.get();
    }

    /**
     * 첫 번째 구독자가 등록될 때 자동으로 연결하는 Flux를 반환합니다.
     *
     * @return 자동 연결 Flux
     */
    public Flux<T> autoConnect() {
        return autoConnect(1);
    }

    /**
     * n명의 구독자가 등록될 때 자동으로 연결하는 Flux를 반환합니다.
     *
     * <p><strong>Warning:</strong> minSubscribers가 0 이하면 즉시 연결됩니다.
     * 구독자가 없는 상태에서 연결하면 데이터가 유실될 수 있습니다.
     *
     * @param minSubscribers 연결을 시작할 최소 구독자 수 (0 이하면 즉시 연결)
     * @return 자동 연결 Flux
     */
    public Flux<T> autoConnect(int minSubscribers) {
        if (minSubscribers <= 0) {
            connect();
            return Flux.from(this);
        }
        return new Flux<>(new AutoConnectPublisher<>(this, minSubscribers));
    }

    /**
     * 구독을 제거합니다.
     */
    void removeSubscription(ConnectableSubscription<T> subscription) {
        subscriptions.remove(subscription);
    }

    /**
     * upstream의 시그널을 모든 구독자에게 멀티캐스트하는 내부 Subscriber.
     */
    private class InnerSubscriber implements Subscriber<T> {

        @Override
        public void onSubscribe(Subscription s) {
            if (upstreamSubscription.compareAndSet(null, s)) {
                // unbounded 요청 (모든 구독자에게 브로드캐스트)
                s.request(Long.MAX_VALUE);
            } else {
                s.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            for (ConnectableSubscription<T> subscription : subscriptions) {
                if (!subscription.isCancelled()) {
                    try {
                        subscription.subscriber.onNext(item);
                    } catch (Throwable t) {
                        // Rule 2.13: Subscriber가 예외를 던지면 해당 구독 취소
                        subscription.cancel();
                    }
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (completed.compareAndSet(false, true)) {
                error = t;
                for (ConnectableSubscription<T> subscription : subscriptions) {
                    if (!subscription.isCancelled()) {
                        try {
                            subscription.subscriber.onError(t);
                        } catch (Throwable ignored) {
                            // Rule 2.13: onError에서 예외 발생 시 무시
                        }
                    }
                }
                subscriptions.clear();
            }
        }

        @Override
        public void onComplete() {
            if (completed.compareAndSet(false, true)) {
                for (ConnectableSubscription<T> subscription : subscriptions) {
                    if (!subscription.isCancelled()) {
                        try {
                            subscription.subscriber.onComplete();
                        } catch (Throwable ignored) {
                            // Rule 2.13: onComplete에서 예외 발생 시 무시
                        }
                    }
                }
                subscriptions.clear();
            }
        }
    }

    /**
     * 자동 연결을 위한 Publisher 래퍼.
     * 
     * <p>스레드 안전성을 위해 AtomicInteger/AtomicBoolean을 사용합니다.
     */
    private static class AutoConnectPublisher<T> implements Publisher<T> {
        private final ConnectableFlux<T> source;
        private final int minSubscribers;
        private final AtomicInteger currentSubscribers = new AtomicInteger(0);
        private final AtomicBoolean connected = new AtomicBoolean(false);

        AutoConnectPublisher(ConnectableFlux<T> source, int minSubscribers) {
            this.source = source;
            this.minSubscribers = minSubscribers;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
            
            // 구독 후 연결 여부 결정 (스레드 안전)
            int count = currentSubscribers.incrementAndGet();
            if (count >= minSubscribers && connected.compareAndSet(false, true)) {
                source.connect();
            }
        }
    }

    /**
     * ConnectableFlux용 Subscription.
     * 
     * <p>Backpressure를 지원하지 않으며 (브로드캐스트 모드),
     * cancel 호출 시 구독자 리스트에서 제거됩니다.
     */
    private static class ConnectableSubscription<T> implements Subscription {
        final Subscriber<? super T> subscriber;
        private final ConnectableFlux<T> parent;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ConnectableSubscription(Subscriber<? super T> subscriber, ConnectableFlux<T> parent) {
            this.subscriber = subscriber;
            this.parent = parent;
        }

        @Override
        public void request(long n) {
            // Rule 3.9: n <= 0이면 에러 시그널
            if (n <= 0) {
                // Rule 1.7: cancel 후에는 시그널을 보내면 안 됨
                // cancelled가 false인 경우에만 onError 호출
                if (cancelled.compareAndSet(false, true)) {
                    parent.removeSubscription(this);
                    subscriber.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
                return;
            }
            // ConnectableFlux는 양수 demand를 무시 (브로드캐스트 모드)
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                parent.removeSubscription(this);
            }
        }
        
        boolean isCancelled() {
            return cancelled.get();
        }
    }

    /**
     * 연결 해제를 위한 Disposable 구현.
     */
    private static class ConnectionDisposable implements Disposable {
        private final AtomicReference<Subscription> subscriptionRef;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        ConnectionDisposable(AtomicReference<Subscription> subscriptionRef) {
            this.subscriptionRef = subscriptionRef;
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                Subscription s = subscriptionRef.get();
                if (s != null) {
                    s.cancel();
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
