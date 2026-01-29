package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 처음 n개의 요소만 통과시키는 Operator.
 *
 * <p>지정된 개수만큼 요소를 전달한 후 upstream을 cancel하고
 * downstream에 onComplete를 보냅니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * upstream:   ──1──2──3──4──5──6──|
 *                      │
 *                  take(3)
 *                      │
 * downstream: ──1──2──3──|
 * </pre>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │                  TakeOperator                       │
 * ├─────────────────────────────────────────────────────┤
 * │                                                     │
 * │  upstream ──onNext(T)──> [count++]                 │
 * │                              │                     │
 * │                   ┌──────────┴──────────┐          │
 * │                   │                     │          │
 * │              count < n            count >= n       │
 * │                   │                     │          │
 * │             onNext(T)            onNext(T)         │
 * │                   │               cancel()         │
 * │                   │             onComplete()       │
 * │                   ▼                     │          │
 * │              downstream           upstream         │
 * │                                                     │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Backpressure</h2>
 * <p>downstream의 request를 limit 이하로 제한하여 upstream에 전달합니다.
 * 불필요한 데이터 생성을 방지합니다.
 *
 * @param <T> 요소 타입
 */
public final class TakeOperator<T> extends AbstractOperator<T, T> {

    private final long limit;

    /**
     * TakeOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param limit 가져올 요소 개수 (양수)
     * @throws NullPointerException upstream이 null인 경우
     * @throws IllegalArgumentException limit이 0 이하인 경우
     */
    public TakeOperator(Publisher<T> upstream, long limit) {
        super(upstream);
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive, but was " + limit);
        }
        this.limit = limit;
    }

    @Override
    protected Subscriber<T> createSubscriber(Subscriber<? super T> downstream) {
        return new TakeSubscriber<>(downstream, limit);
    }

    /**
     * Take를 수행하는 Subscriber.
     *
     * <p>AtomicLong을 사용하여 스레드 안전성을 보장합니다.
     * request를 limit에 맞게 제한하는 특수한 로직이 필요하므로
     * request()를 오버라이드합니다.
     *
     * @param <T> 요소 타입
     */
    private static final class TakeSubscriber<T> extends AbstractOperatorSubscriber<T, T> {

        private final long limit;
        private final AtomicLong count = new AtomicLong(0);

        TakeSubscriber(Subscriber<? super T> downstream, long limit) {
            super(downstream);
            this.limit = limit;
        }

        @Override
        public void onNext(T item) {
            if (isDone()) {
                return;
            }

            long c = count.incrementAndGet();
            downstream.onNext(item);

            if (c >= limit) {
                // limit에 도달하면 완료
                if (markDone()) {
                    cancelUpstream();
                    downstream.onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
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

        /**
         * downstream의 request를 남은 개수에 맞게 제한하여 upstream에 전달합니다.
         *
         * <p>불필요한 데이터 생성을 방지하기 위해 limit - count 이하로만 요청합니다.
         *
         * @param n 요청할 데이터 개수
         */
        @Override
        public void request(long n) {
            if (n <= 0) {
                return;
            }

            // 이미 완료되었으면 무시
            if (isDone()) {
                return;
            }

            // 남은 개수만큼만 요청 (불필요한 데이터 생성 방지)
            long currentCount = count.get();
            long remaining = limit - currentCount;

            if (remaining <= 0) {
                return;
            }

            // n과 remaining 중 작은 값만 요청
            long toRequest = Math.min(n, remaining);
            super.request(toRequest);
        }
    }
}
