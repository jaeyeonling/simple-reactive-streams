package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.scheduler.Scheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 구독 시점의 스레드를 변경하는 Operator.
 *
 * <p>subscribe() 호출이 Scheduler에서 실행되도록 합니다.
 * 결과적으로 upstream의 데이터 생성도 해당 Scheduler에서 실행됩니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    SubscribeOnOperator                      │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │  Main Thread                Scheduler Thread                │
 * │  ───────────                ────────────────                │
 * │                                                             │
 * │  subscribe() ─────────────> upstream.subscribe()            │
 * │                                    │                        │
 * │                               onSubscribe                   │
 * │                               onNext(1)                     │
 * │                               onNext(2)                     │
 * │                               onComplete                    │
 * │                                    │                        │
 * │                                    ▼                        │
 * │                              downstream                     │
 * │                                                             │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * [main thread]
 *     │
 *     │ subscribe()
 *     ▼
 * subscribeOn(single)
 *     │
 *     │ ──────────────────────> [single-1 thread]
 *     │                              │
 *     │                         upstream.subscribe()
 *     │                              │
 *     │                         ──1──2──3──|
 *     │                              │
 *     ▼                              ▼
 * downstream receives on [single-1 thread]
 * </pre>
 *
 * <h2>subscribeOn vs publishOn</h2>
 * <ul>
 *   <li>subscribeOn: 구독이 시작되는 스레드 결정 (위치 무관, 가장 먼저 적용된 것만 유효)</li>
 *   <li>publishOn: 이후 연산자들이 실행되는 스레드 결정 (위치 중요)</li>
 * </ul>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>subscribeOn은 체인에서 어디에 있든 구독 시점에 영향</li>
 *   <li>여러 subscribeOn이 있으면 가장 먼저(가장 upstream 가까이) 선언된 것만 유효</li>
 *   <li>Cold Publisher의 데이터 생성 스레드를 변경할 때 사용</li>
 * </ul>
 *
 * @param <T> 요소 타입
 * @see PublishOnOperator
 */
public final class SubscribeOnOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final Scheduler scheduler;

    /**
     * SubscribeOnOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param scheduler 구독을 실행할 Scheduler
     * @throws NullPointerException upstream 또는 scheduler가 null인 경우
     */
    public SubscribeOnOperator(Publisher<T> upstream, Scheduler scheduler) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Scheduler must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Rule 1.9: Subscriber must not be null");

        // downstream에 먼저 subscription 전달 (request 버퍼링 지원)
        SubscribeOnSubscriber<T> subscribeOnSubscriber = new SubscribeOnSubscriber<>(subscriber);
        subscriber.onSubscribe(subscribeOnSubscriber);
        
        // Scheduler에서 구독 실행
        scheduler.schedule(() -> upstream.subscribe(subscribeOnSubscriber));
    }

    /**
     * 시그널을 전달하는 Subscriber.
     *
     * <p>비동기 구독을 지원하기 위해 request를 버퍼링합니다.
     * upstream.subscribe()가 비동기로 실행되므로,
     * downstream의 request()가 먼저 호출될 수 있습니다.
     */
    private static final class SubscribeOnSubscriber<T> implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> downstream;
        private final AtomicReference<Subscription> upstream = new AtomicReference<>();
        private final AtomicLong pendingRequests = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);

        SubscribeOnSubscriber(Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream.compareAndSet(null, s)) {
                // 취소되었으면 upstream도 취소
                if (cancelled.get()) {
                    s.cancel();
                    return;
                }
                
                // 버퍼링된 request가 있으면 upstream에 전달
                long pending = pendingRequests.getAndSet(0);
                if (pending > 0) {
                    s.request(pending);
                }
            } else {
                s.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            // Rule 2.13: null 체크
            if (item == null) {
                cancelUpstream();
                onError(new NullPointerException("Rule 2.13: onNext item must not be null"));
                return;
            }
            
            if (done.get()) {
                return;
            }
            
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            // Rule 2.13: null 체크
            if (t == null) {
                t = new NullPointerException("Rule 2.13: onError throwable must not be null");
            }
            
            if (done.compareAndSet(false, true)) {
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                if (done.compareAndSet(false, true)) {
                    downstream.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
                return;
            }
            
            Subscription s = upstream.get();
            if (s != null) {
                s.request(n);
            } else {
                // upstream이 아직 설정되지 않았으면 버퍼링
                addPendingRequests(n);
                
                // 다시 확인 - race condition 방지
                s = upstream.get();
                if (s != null) {
                    long pending = pendingRequests.getAndSet(0);
                    if (pending > 0) {
                        s.request(pending);
                    }
                }
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
            }
        }
        
        private void cancelUpstream() {
            Subscription s = upstream.get();
            if (s != null) {
                s.cancel();
            }
        }
        
        private void addPendingRequests(long n) {
            pendingRequests.getAndUpdate(current -> {
                if (current == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                long next = current + n;
                return next < 0 ? Long.MAX_VALUE : next;
            });
        }
    }
}
