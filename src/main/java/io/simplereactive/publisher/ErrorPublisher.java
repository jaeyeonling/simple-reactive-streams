package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 즉시 에러를 발행하는 Publisher.
 *
 * <p>구독 시 onSubscribe를 호출한 후 즉시 onError를 호출합니다.
 * 데이터를 발행하지 않고 에러만 전달합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * ──✗  (즉시 에러)
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Publisher<String> error = new ErrorPublisher<>(new RuntimeException("Oops!"));
 * error.subscribe(subscriber);
 * // subscriber.onSubscribe() 호출
 * // subscriber.request(n) 시 즉시 onError() 호출
 * }</pre>
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 1.4: 에러 발생 시 onError를 시그널해야 한다</li>
 *   <li>Rule 1.7: onError 후에는 어떤 시그널도 발생하면 안 된다</li>
 * </ul>
 *
 * @param <T> 요소 타입 (실제로는 발행되지 않음)
 */
public final class ErrorPublisher<T> implements Publisher<T> {

    private final Throwable error;

    /**
     * ErrorPublisher를 생성합니다.
     *
     * @param error 발행할 에러
     * @throws NullPointerException error가 null인 경우
     */
    public ErrorPublisher(Throwable error) {
        this.error = Objects.requireNonNull(error, "Error must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
        }
        subscriber.onSubscribe(new ErrorSubscription(subscriber, error));
    }

    /**
     * 에러 Subscription 구현.
     *
     * <p>request 시 즉시 onError를 호출합니다.
     */
    private static final class ErrorSubscription implements Subscription {

        private final Subscriber<?> subscriber;
        private final Throwable error;
        private final AtomicBoolean done = new AtomicBoolean(false);

        ErrorSubscription(Subscriber<?> subscriber, Throwable error) {
            this.subscriber = subscriber;
            this.error = error;
        }

        @Override
        public void request(long n) {
            // Rule 3.9: n <= 0이면 다른 에러로 처리
            if (n <= 0) {
                if (done.compareAndSet(false, true)) {
                    subscriber.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
                return;
            }

            // Rule 1.4: 에러 시그널
            if (done.compareAndSet(false, true)) {
                subscriber.onError(error);
            }
        }

        @Override
        public void cancel() {
            done.set(true);
        }
    }
}
