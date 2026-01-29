package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

import java.util.Objects;

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
 * @see TakeSubscriber
 */
public final class TakeOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
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
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive, but was " + limit);
        }
        this.limit = limit;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(new TakeSubscriber<>(subscriber, limit));
    }
}
