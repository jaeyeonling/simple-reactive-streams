package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.scheduler.Scheduler;

import java.util.Objects;

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
        Objects.requireNonNull(subscriber, "Subscriber must not be null");

        // Scheduler에서 구독 실행
        scheduler.schedule(() -> upstream.subscribe(new SubscribeOnSubscriber<>(subscriber)));
    }

    /**
     * 단순히 시그널을 전달하는 Subscriber.
     *
     * <p>upstream의 시그널을 그대로 downstream에 전달합니다.
     * 스레드 전환은 이미 subscribe() 시점에 이루어졌으므로
     * 추가적인 처리는 필요 없습니다.
     */
    private static final class SubscribeOnSubscriber<T> implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> downstream;
        private Subscription upstream;

        SubscribeOnSubscriber(Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            downstream.onError(t);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
        }
    }
}
