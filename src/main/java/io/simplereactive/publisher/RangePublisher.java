package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

/**
 * 정수 범위를 발행하는 Publisher.
 *
 * <p>start부터 시작하여 count개의 연속된 정수를 발행합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * range(1, 5): ──1──2──3──4──5──|
 * range(10, 3): ──10──11──12──|
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Publisher<Integer> range = new RangePublisher(1, 5);
 * range.subscribe(subscriber);
 * subscriber.request(3);  // 1, 2, 3 발행
 * subscriber.request(10); // 4, 5 발행 후 onComplete
 * }</pre>
 *
 * <h2>Backpressure</h2>
 * <p>request(n)만큼만 발행하며, 모든 요소 발행 후 onComplete를 호출합니다.
 *
 * @see RangeSubscription
 */
public final class RangePublisher implements Publisher<Integer> {

    private final int start;
    private final int count;

    /**
     * RangePublisher를 생성합니다.
     *
     * @param start 시작값 (포함)
     * @param count 발행할 개수
     * @throws IllegalArgumentException count가 음수인 경우
     */
    public RangePublisher(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must not be negative, but was " + count);
        }
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("Subscriber must not be null");
        }
        subscriber.onSubscribe(new RangeSubscription(subscriber, start, count));
    }
}
