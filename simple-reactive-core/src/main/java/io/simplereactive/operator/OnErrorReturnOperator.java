package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

import java.util.Objects;
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
public final class OnErrorReturnOperator<T> extends AbstractOperator<T, T> {

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
        super(upstream);
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
        super(upstream);
        Objects.requireNonNull(defaultValue, "Default value must not be null");
        this.fallback = error -> defaultValue;
    }

    @Override
    protected Subscriber<T> createSubscriber(Subscriber<? super T> downstream) {
        return new OnErrorReturnSubscriber<>(downstream, fallback);
    }

    /**
     * OnErrorReturn을 수행하는 Subscriber.
     */
    private static final class OnErrorReturnSubscriber<T> extends AbstractOperatorSubscriber<T, T> {

        private final Function<? super Throwable, ? extends T> fallback;

        OnErrorReturnSubscriber(
                Subscriber<? super T> downstream,
                Function<? super Throwable, ? extends T> fallback) {
            super(downstream);
            this.fallback = fallback;
        }

        @Override
        public void onNext(T item) {
            if (isDone()) {
                return;
            }
            
            // Rule 2.13: null 체크
            if (item == null) {
                cancelUpstream();
                onError(new NullPointerException("Rule 2.13: onNext called with null"));
                return;
            }
            
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            if (isDone()) {
                return;
            }

            // fallback으로 기본값 생성
            T defaultValue;
            try {
                defaultValue = fallback.apply(t);
            } catch (Throwable ex) {
                // fallback 함수에서 예외 발생 시 원래 에러와 함께 전달
                ex.addSuppressed(t);
                if (markDone()) {
                    cancelUpstream();
                    downstream.onError(ex);
                }
                return;
            }

            // Rule 2.13: null 체크
            if (defaultValue == null) {
                if (markDone()) {
                    cancelUpstream();
                    downstream.onError(new NullPointerException(
                            "Rule 2.13: Fallback returned null for error: " + t));
                }
                return;
            }

            // 기본값 발행 후 완료
            if (markDone()) {
                downstream.onNext(defaultValue);
                downstream.onComplete();
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
