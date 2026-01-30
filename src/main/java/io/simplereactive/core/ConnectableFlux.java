package io.simplereactive.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <h2>autoConnect vs refCount</h2>
 * <ul>
 *   <li>autoConnect(n): n명 구독 시 자동 연결</li>
 *   <li>refCount(): 첫 구독자 시 연결, 마지막 구독자 취소 시 연결 해제</li>
 * </ul>
 *
 * @param <T> 요소 타입
 * @see Flux#publish()
 * @see Flux#share()
 */
public class ConnectableFlux<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final List<Subscriber<? super T>> subscribers = new CopyOnWriteArrayList<>();
    
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicReference<Subscription> upstreamSubscription = new AtomicReference<>();
    
    // error를 먼저 설정하고 completed를 나중에 설정 (메모리 가시성)
    private volatile Throwable error;

    /**
     * ConnectableFlux를 생성합니다.
     *
     * @param upstream 원본 Cold Publisher
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

        if (completed.get()) {
            // 이미 완료된 경우 - 즉시 종료 시그널
            subscriber.onSubscribe(new EmptySubscription());
            Throwable ex = error;
            if (ex != null) {
                subscriber.onError(ex);
            } else {
                subscriber.onComplete();
            }
            return;
        }

        ConnectableSubscription<T> subscription = new ConnectableSubscription<>(subscriber, this);
        subscribers.add(subscriber);
        subscriber.onSubscribe(subscription);
    }

    /**
     * upstream에 연결하여 데이터 발행을 시작합니다.
     *
     * <p>한 번만 호출할 수 있습니다. 이미 연결된 경우 무시됩니다.
     * 연결 후 모든 구독자에게 데이터가 발행됩니다.
     *
     * @return 연결 해제를 위한 Disposable (cancel 호출용)
     */
    public Disposable connect() {
        if (connected.compareAndSet(false, true)) {
            upstream.subscribe(new InnerSubscriber());
        }
        return () -> {
            Subscription s = upstreamSubscription.get();
            if (s != null) {
                s.cancel();
            }
        };
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
     * @param minSubscribers 연결을 시작할 최소 구독자 수
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
    void removeSubscriber(Subscriber<? super T> subscriber) {
        subscribers.remove(subscriber);
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
            for (Subscriber<? super T> subscriber : subscribers) {
                subscriber.onNext(item);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (completed.compareAndSet(false, true)) {
                error = t;
                for (Subscriber<? super T> subscriber : subscribers) {
                    subscriber.onError(t);
                }
                subscribers.clear();
            }
        }

        @Override
        public void onComplete() {
            if (completed.compareAndSet(false, true)) {
                for (Subscriber<? super T> subscriber : subscribers) {
                    subscriber.onComplete();
                }
                subscribers.clear();
            }
        }
    }

    /**
     * 연결 해제를 위한 함수형 인터페이스.
     */
    @FunctionalInterface
    public interface Disposable {
        void dispose();
    }

    /**
     * 자동 연결을 위한 Publisher 래퍼.
     */
    private static class AutoConnectPublisher<T> implements Publisher<T> {
        private final ConnectableFlux<T> source;
        private final int minSubscribers;
        private int currentSubscribers = 0;
        private boolean connected = false;

        AutoConnectPublisher(ConnectableFlux<T> source, int minSubscribers) {
            this.source = source;
            this.minSubscribers = minSubscribers;
        }

        @Override
        public synchronized void subscribe(Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
            currentSubscribers++;
            if (!connected && currentSubscribers >= minSubscribers) {
                connected = true;
                source.connect();
            }
        }
    }

    /**
     * ConnectableFlux용 Subscription.
     */
    private static class ConnectableSubscription<T> implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final ConnectableFlux<T> parent;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ConnectableSubscription(Subscriber<? super T> subscriber, ConnectableFlux<T> parent) {
            this.subscriber = subscriber;
            this.parent = parent;
        }

        @Override
        public void request(long n) {
            // ConnectableFlux는 demand를 무시 (브로드캐스트 모드)
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                parent.removeSubscriber(subscriber);
            }
        }
    }

    /**
     * 빈 Subscription.
     */
    private static class EmptySubscription implements Subscription {
        @Override
        public void request(long n) {}
        @Override
        public void cancel() {}
    }
}
