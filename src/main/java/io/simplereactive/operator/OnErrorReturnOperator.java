package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 에러 발생 시 기본값을 반환하는 Operator.
 *
 * <p>upstream에서 에러가 발생하면 fallback 함수로 기본값을 생성하여
 * 발행한 후 완료합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * upstream:   ──1──2──✗
 *                     │
 *              onErrorReturn(e -> -1)
 *                     │
 * downstream: ──1──2──(-1)──|
 * </pre>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │             OnErrorReturnOperator                   │
 * ├─────────────────────────────────────────────────────┤
 * │                                                     │
 * │  upstream ──onNext──> downstream                   │
 * │                                                     │
 * │  upstream ──onError──> [fallback.apply(error)]     │
 * │                              │                     │
 * │                         defaultValue               │
 * │                              │                     │
 * │                    onNext(defaultValue)            │
 * │                         onComplete()               │
 * │                              │                     │
 * │                         downstream                 │
 * │                                                     │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * @param <T> 요소 타입
 */
public final class OnErrorReturnOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final Function<? super Throwable, ? extends T> fallback;

    /**
     * OnErrorReturnOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param fallback 에러 발생 시 기본값을 반환하는 함수
     * @throws NullPointerException upstream 또는 fallback이 null인 경우
     */
    public OnErrorReturnOperator(
            Publisher<T> upstream,
            Function<? super Throwable, ? extends T> fallback) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.fallback = Objects.requireNonNull(fallback, "Fallback must not be null");
    }

    /**
     * 고정 기본값을 사용하는 OnErrorReturnOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param defaultValue 에러 발생 시 반환할 기본값
     * @throws NullPointerException upstream 또는 defaultValue가 null인 경우
     */
    public OnErrorReturnOperator(Publisher<T> upstream, T defaultValue) {
        this(upstream, error -> Objects.requireNonNull(defaultValue, "Default value must not be null"));
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(new OnErrorReturnSubscriber<>(subscriber, fallback));
    }

    /**
     * OnErrorReturn을 수행하는 Subscriber.
     */
    private static final class OnErrorReturnSubscriber<T> implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> downstream;
        private final Function<? super Throwable, ? extends T> fallback;
        
        private Subscription upstream;
        private final AtomicBoolean done = new AtomicBoolean(false);

        OnErrorReturnSubscriber(
                Subscriber<? super T> downstream,
                Function<? super Throwable, ? extends T> fallback) {
            this.downstream = downstream;
            this.fallback = fallback;
        }

        // ========== Subscriber 구현 ==========

        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            if (done.get()) {
                return;
            }
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            if (done.get()) {
                return;
            }

            // fallback으로 기본값 생성
            T defaultValue;
            try {
                defaultValue = fallback.apply(t);
            } catch (Throwable ex) {
                // fallback 함수에서 예외 발생 시 원래 에러와 함께 전달
                ex.addSuppressed(t);
                if (done.compareAndSet(false, true)) {
                    downstream.onError(ex);
                }
                return;
            }

            // Rule 2.13: null 체크
            if (defaultValue == null) {
                if (done.compareAndSet(false, true)) {
                    downstream.onError(new NullPointerException(
                            "Fallback returned null for error: " + t));
                }
                return;
            }

            // 기본값 발행 후 완료
            if (done.compareAndSet(false, true)) {
                downstream.onNext(defaultValue);
                downstream.onComplete();
            }
        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                downstream.onComplete();
            }
        }

        // ========== Subscription 구현 ==========

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            if (done.compareAndSet(false, true)) {
                upstream.cancel();
            }
        }
    }
}
