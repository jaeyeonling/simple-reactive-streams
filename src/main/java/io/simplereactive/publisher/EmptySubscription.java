package io.simplereactive.publisher;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 빈 Subscription 구현.
 *
 * <p>request 시 즉시 onComplete를 호출합니다.
 * AtomicBoolean을 사용하여 스레드 안전성을 보장합니다.
 *
 * <h2>Reactive Streams 규약</h2>
 * <ul>
 *   <li>Rule 3.9: request(n)에서 n <= 0이면 onError 호출</li>
 * </ul>
 *
 * @see EmptyPublisher
 */
final class EmptySubscription implements Subscription {

    private final Subscriber<?> subscriber;
    private final AtomicBoolean done = new AtomicBoolean(false);

    /**
     * EmptySubscription을 생성합니다.
     *
     * @param subscriber downstream Subscriber
     */
    EmptySubscription(Subscriber<?> subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
        // Rule 3.9: n <= 0이면 에러
        if (n <= 0) {
            if (done.compareAndSet(false, true)) {
                subscriber.onError(new IllegalArgumentException(
                        "Rule 3.9: request amount must be positive, but was " + n));
            }
            return;
        }

        // compareAndSet으로 단 한 번만 onComplete 호출 보장
        if (done.compareAndSet(false, true)) {
            subscriber.onComplete();
        }
    }

    @Override
    public void cancel() {
        done.set(true);
    }
}
