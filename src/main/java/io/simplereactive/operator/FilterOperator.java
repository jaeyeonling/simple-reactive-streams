package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 조건에 맞는 요소만 통과시키는 Operator.
 *
 * <p>upstream에서 받은 요소 중 predicate를 만족하는 요소만
 * downstream으로 전달합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * upstream:   ──1──2──3──4──5──6──|
 *                      │
 *              filter(x -> x % 2 == 0)
 *                      │
 * downstream: ─────2─────4─────6──|
 * </pre>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────┐
 * │               FilterOperator                    │
 * ├─────────────────────────────────────────────────┤
 * │                                                 │
 * │  upstream ──onNext(T)──> [predicate.test(T)]   │
 * │                               │                │
 * │                    ┌──────────┴──────────┐     │
 * │                    │                     │     │
 * │                  true                  false   │
 * │                    │                     │     │
 * │              onNext(T)            request(1)   │
 * │                    │               (보충 요청) │
 * │                    ▼                     │     │
 * │               downstream           upstream    │
 * │                                                 │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Backpressure</h2>
 * <p>필터링된 요소는 downstream에 전달되지 않으므로,
 * 필터링된 만큼 upstream에 추가로 요청해야 합니다.
 * 그렇지 않으면 downstream이 요청한 개수보다 적게 받을 수 있습니다.
 *
 * @param <T> 요소 타입
 */
public class FilterOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final Predicate<? super T> predicate;

    /**
     * FilterOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param predicate 필터 조건
     * @throws NullPointerException upstream 또는 predicate가 null인 경우
     */
    public FilterOperator(Publisher<T> upstream, Predicate<? super T> predicate) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.predicate = Objects.requireNonNull(predicate, "Predicate must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(new FilterSubscriber<>(subscriber, predicate));
    }

    /**
     * Filter를 수행하는 Subscriber.
     *
     * <p>Subscription도 구현하여 downstream의 request를 중개합니다.
     * 필터링된 요소에 대해 추가 request를 보내기 위함입니다.
     *
     * @param <T> 요소 타입
     */
    static class FilterSubscriber<T> implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> downstream;
        private final Predicate<? super T> predicate;
        private Subscription upstream;
        private boolean done = false;

        FilterSubscriber(Subscriber<? super T> downstream, Predicate<? super T> predicate) {
            this.downstream = downstream;
            this.predicate = predicate;
        }

        // ========== Subscriber 구현 ==========

        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            // 우리가 만든 Subscription(this)을 전달
            // 필터링된 요소에 대한 추가 request를 처리하기 위함
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            if (done) {
                return;
            }

            boolean matches;
            try {
                matches = predicate.test(item);
            } catch (Throwable t) {
                // predicate에서 예외 발생 시 에러 처리
                upstream.cancel();
                onError(t);
                return;
            }

            if (matches) {
                downstream.onNext(item);
            } else {
                // 필터링된 요소는 전달되지 않으므로 추가 요청
                // downstream이 n개 요청했으면 n개를 받아야 함
                upstream.request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            done = true;
            downstream.onError(t);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            downstream.onComplete();
        }

        // ========== Subscription 구현 ==========

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
