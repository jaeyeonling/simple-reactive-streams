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
     * 시그널을 전달하는 Subscriber.
     *
     * <p>AbstractOperatorSubscriber를 상속하여 다음을 보장합니다:
     * <ul>
     *   <li>스레드 안전한 upstream 관리 (AtomicReference)</li>
     *   <li>중복 onSubscribe 방지 (Rule 2.12)</li>
     *   <li>종료 후 시그널 차단 (Rule 1.7)</li>
     *   <li>null 체크 (Rule 2.13)</li>
     * </ul>
     */
    private static final class SubscribeOnSubscriber<T> extends AbstractOperatorSubscriber<T, T> {

        SubscribeOnSubscriber(Subscriber<? super T> downstream) {
            super(downstream);
        }

        @Override
        public void onNext(T item) {
            // Rule 2.13: null 체크
            if (item == null) {
                cancelUpstream();
                onError(new NullPointerException("Rule 2.13: onNext item must not be null"));
                return;
            }
            
            if (isDone()) {
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
            
            if (markDone()) {
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (markDone()) {
                downstream.onComplete();
            }
        }
    }
}
