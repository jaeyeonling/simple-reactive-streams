package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 구독 시점에 값을 생성하는 지연 실행 Publisher.
 *
 * <p>Cold Publisher로, 구독할 때마다 Callable을 실행하여 값을 생성합니다.
 * 블로킹 API를 Publisher로 래핑할 때 유용합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // 블로킹 API를 Publisher로 래핑
 * Publisher<Product> publisher = new DeferPublisher<>(() ->
 *     productApi.getProduct(productId)  // 블로킹 호출
 * );
 *
 * // subscribeOn과 함께 사용하여 논블로킹 처리
 * Flux.from(publisher)
 *     .subscribeOn(Schedulers.io())
 *     .subscribe(subscriber);
 * }</pre>
 *
 * <h2>Callable vs Supplier</h2>
 * <p>{@link Callable}을 사용하여 checked exception을 throw할 수 있습니다.
 * 예외 발생 시 {@link Subscriber#onError(Throwable)}로 전달됩니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * subscribe()
 *     │
 *     ▼
 * Callable.call()  ─────> "result"
 *     │
 *     ▼
 * onNext("result") → onComplete
 * </pre>
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 1.1: subscribe 시 onSubscribe를 먼저 호출</li>
 *   <li>Rule 1.4: onNext 후 onComplete 호출</li>
 *   <li>Rule 1.9: subscriber가 null이면 NullPointerException</li>
 *   <li>Rule 2.13: null 값 발행 시 onError</li>
 * </ul>
 *
 * @param <T> 발행할 요소의 타입
 * @see io.simplereactive.core.Flux#subscribeOn
 */
public class DeferPublisher<T> implements Publisher<T> {

    private final Callable<T> callable;

    /**
     * 주어진 Callable로 DeferPublisher를 생성합니다.
     *
     * @param callable 구독 시 실행할 Callable (null 불가)
     * @throws NullPointerException callable이 null인 경우
     */
    public DeferPublisher(Callable<T> callable) {
        this.callable = Objects.requireNonNull(callable, "Callable must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Subscriber에게 {@link DeferSubscription}을 전달합니다.
     * request(n)이 호출되면 Callable을 실행하여 값을 발행합니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
        }

        // Rule 1.1: onSubscribe 호출
        DeferSubscription<T> subscription = new DeferSubscription<>(subscriber, callable);
        subscriber.onSubscribe(subscription);
    }

    /**
     * DeferPublisher용 Subscription.
     *
     * <p>request가 호출되면 Callable을 실행하고 결과를 발행합니다.
     * 한 번만 발행하고 완료됩니다.
     */
    private static class DeferSubscription<T> implements Subscription {

        private final Subscriber<? super T> subscriber;
        private final Callable<T> callable;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);

        DeferSubscription(Subscriber<? super T> subscriber, Callable<T> callable) {
            this.subscriber = subscriber;
            this.callable = callable;
        }

        @Override
        public void request(long n) {
            // Rule 3.9: n <= 0이면 에러
            if (n <= 0) {
                if (cancelled.compareAndSet(false, true)) {
                    if (completed.compareAndSet(false, true)) {
                        subscriber.onError(new IllegalArgumentException(
                                "Rule 3.9: request amount must be positive, but was " + n));
                    }
                }
                return;
            }

            // 이미 취소되었거나 완료된 경우 무시
            if (cancelled.get() || completed.get()) {
                return;
            }

            // 한 번만 실행
            if (completed.compareAndSet(false, true)) {
                try {
                    T value = callable.call();

                    // Rule 2.13: null 체크
                    if (value == null) {
                        if (!cancelled.get()) {
                            subscriber.onError(new NullPointerException(
                                    "Rule 2.13: Callable returned null"));
                        }
                        return;
                    }

                    // 취소되지 않았으면 발행
                    if (!cancelled.get()) {
                        subscriber.onNext(value);
                    }

                    // 취소되지 않았으면 완료
                    if (!cancelled.get()) {
                        subscriber.onComplete();
                    }
                } catch (Throwable t) {
                    // Rule 1.4: 에러 발생 시 onError 호출
                    if (!cancelled.get()) {
                        subscriber.onError(t);
                    }
                }
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
