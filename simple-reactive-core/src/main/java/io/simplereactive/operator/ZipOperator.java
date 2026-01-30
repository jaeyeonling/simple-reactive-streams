package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.core.TriFunction;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * 여러 Publisher의 결과를 조합하는 Operator.
 *
 * <p>각 Publisher가 하나씩 값을 발행하면, combinator 함수로 결과를 조합하여
 * 하나의 값을 발행합니다. 모든 Publisher가 최소 하나의 값을 발행해야
 * 결과가 발행됩니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // 두 Publisher 조합
 * Publisher<String> result = ZipOperator.zip(
 *     publisherA,
 *     publisherB,
 *     (a, b) -> a + "-" + b
 * );
 *
 * // 세 Publisher 조합
 * Publisher<ProductDetail> detail = ZipOperator.zip(
 *     getProduct(id),
 *     getReviews(id),
 *     getInventory(id),
 *     (product, reviews, inventory) ->
 *         new ProductDetail(product, reviews, inventory)
 * );
 * }</pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * source1: ──A────────────|
 * source2: ────────B──────|
 *               │
 *          zip((a,b) -> a+b)
 *               │
 * result:  ────────(A+B)──|
 * </pre>
 *
 * <h2>에러 처리</h2>
 * <p>어느 하나의 Publisher에서 에러가 발생하면 즉시 에러를 전파하고
 * 나머지 Publisher를 취소합니다.
 *
 * <h2>Backpressure</h2>
 * <p>각 Publisher에 Long.MAX_VALUE를 요청합니다 (unbounded).
 * 이 구현은 각 Publisher가 단일 값을 발행하는 경우에 최적화되어 있습니다.
 *
 * @param <T1> 첫 번째 Publisher의 요소 타입
 * @param <T2> 두 번째 Publisher의 요소 타입
 * @param <R> 결과 타입
 */
public class ZipOperator<T1, T2, R> implements Publisher<R> {

    private final Publisher<T1> source1;
    private final Publisher<T2> source2;
    private final BiFunction<? super T1, ? super T2, ? extends R> combinator;

    /**
     * ZipOperator를 생성합니다.
     *
     * @param source1 첫 번째 Publisher
     * @param source2 두 번째 Publisher
     * @param combinator 조합 함수
     * @throws NullPointerException 인자가 null인 경우
     */
    public ZipOperator(Publisher<T1> source1,
                       Publisher<T2> source2,
                       BiFunction<? super T1, ? super T2, ? extends R> combinator) {
        this.source1 = Objects.requireNonNull(source1, "Source1 must not be null");
        this.source2 = Objects.requireNonNull(source2, "Source2 must not be null");
        this.combinator = Objects.requireNonNull(combinator, "Combinator must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
        }

        ZipCoordinator<T1, T2, R> coordinator = new ZipCoordinator<>(
                subscriber, source1, source2, combinator);
        subscriber.onSubscribe(coordinator);
        coordinator.subscribe();
    }

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 두 Publisher를 조합합니다.
     *
     * @param source1 첫 번째 Publisher
     * @param source2 두 번째 Publisher
     * @param combinator 조합 함수
     * @param <T1> 첫 번째 타입
     * @param <T2> 두 번째 타입
     * @param <R> 결과 타입
     * @return 조합된 Publisher
     */
    public static <T1, T2, R> Publisher<R> zip(
            Publisher<T1> source1,
            Publisher<T2> source2,
            BiFunction<? super T1, ? super T2, ? extends R> combinator) {
        return new ZipOperator<>(source1, source2, combinator);
    }

    /**
     * 세 Publisher를 조합합니다.
     *
     * @param source1 첫 번째 Publisher
     * @param source2 두 번째 Publisher
     * @param source3 세 번째 Publisher
     * @param combinator 조합 함수
     * @param <T1> 첫 번째 타입
     * @param <T2> 두 번째 타입
     * @param <T3> 세 번째 타입
     * @param <R> 결과 타입
     * @return 조합된 Publisher
     * @see TriFunction
     */
    public static <T1, T2, T3, R> Publisher<R> zip(
            Publisher<T1> source1,
            Publisher<T2> source2,
            Publisher<T3> source3,
            TriFunction<? super T1, ? super T2, ? super T3, ? extends R> combinator) {
        Objects.requireNonNull(source1, "Source1 must not be null");
        Objects.requireNonNull(source2, "Source2 must not be null");
        Objects.requireNonNull(source3, "Source3 must not be null");
        Objects.requireNonNull(combinator, "Combinator must not be null");
        return new Zip3Publisher<>(source1, source2, source3, combinator);
    }

    // ========== Zip2 Coordinator ==========

    /**
     * 두 Publisher의 조합을 관리하는 Coordinator.
     */
    private static class ZipCoordinator<T1, T2, R> implements Subscription {

        private final Subscriber<? super R> downstream;
        private final Publisher<T1> source1;
        private final Publisher<T2> source2;
        private final BiFunction<? super T1, ? super T2, ? extends R> combinator;

        private final AtomicReference<T1> value1 = new AtomicReference<>();
        private final AtomicReference<T2> value2 = new AtomicReference<>();

        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final AtomicBoolean requested = new AtomicBoolean(false);

        private volatile Subscription subscription1;
        private volatile Subscription subscription2;

        ZipCoordinator(Subscriber<? super R> downstream,
                       Publisher<T1> source1,
                       Publisher<T2> source2,
                       BiFunction<? super T1, ? super T2, ? extends R> combinator) {
            this.downstream = downstream;
            this.source1 = source1;
            this.source2 = source2;
            this.combinator = combinator;
        }

        void subscribe() {
            source1.subscribe(new InnerSubscriber<>(this, 1));
            source2.subscribe(new InnerSubscriber<>(this, 2));
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                if (done.compareAndSet(false, true)) {
                    downstream.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
                return;
            }

            if (requested.compareAndSet(false, true)) {
                // 각 source에 unbounded 요청
                Subscription s1 = subscription1;
                Subscription s2 = subscription2;
                if (s1 != null) {
                    s1.request(Long.MAX_VALUE);
                }
                if (s2 != null) {
                    s2.request(Long.MAX_VALUE);
                }
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Subscription s1 = subscription1;
                Subscription s2 = subscription2;
                if (s1 != null) {
                    s1.cancel();
                }
                if (s2 != null) {
                    s2.cancel();
                }
            }
        }

        @SuppressWarnings("unchecked")
        void onNext(Object value, int index) {
            if (cancelled.get() || done.get()) {
                return;
            }

            if (index == 1) {
                value1.set((T1) value);
            } else {
                value2.set((T2) value);
            }

            tryComplete();
        }

        void onError(Throwable t) {
            if (cancelled.get()) {
                return;
            }

            cancel();
            if (done.compareAndSet(false, true)) {
                downstream.onError(t);
            }
        }

        void onComplete(int index) {
            if (cancelled.get() || done.get()) {
                return;
            }

            completedCount.incrementAndGet();
            tryComplete();
        }

        private void tryComplete() {
            // 모든 값이 준비되고 모든 source가 완료되었는지 확인
            T1 v1 = value1.get();
            T2 v2 = value2.get();

            if (v1 != null && v2 != null && !done.get()) {
                if (done.compareAndSet(false, true)) {
                    try {
                        R result = combinator.apply(v1, v2);
                        if (result == null) {
                            downstream.onError(new NullPointerException(
                                    "Rule 2.13: Combinator returned null"));
                            return;
                        }
                        if (!cancelled.get()) {
                            downstream.onNext(result);
                            downstream.onComplete();
                        }
                    } catch (Throwable t) {
                        downstream.onError(t);
                    }
                }
            }
        }

        void setSubscription(Subscription s, int index) {
            if (index == 1) {
                subscription1 = s;
            } else {
                subscription2 = s;
            }

            // request가 이미 호출되었으면 즉시 요청
            if (requested.get() && !cancelled.get()) {
                s.request(Long.MAX_VALUE);
            }
        }
    }

    /**
     * 각 source에 대한 내부 Subscriber.
     */
    private static class InnerSubscriber<T1, T2, R> implements Subscriber<Object> {

        private final ZipCoordinator<T1, T2, R> parent;
        private final int index;

        InnerSubscriber(ZipCoordinator<T1, T2, R> parent, int index) {
            this.parent = parent;
            this.index = index;
        }

        @Override
        public void onSubscribe(Subscription s) {
            parent.setSubscription(s, index);
        }

        @Override
        public void onNext(Object item) {
            parent.onNext(item, index);
        }

        @Override
        public void onError(Throwable t) {
            parent.onError(t);
        }

        @Override
        public void onComplete() {
            parent.onComplete(index);
        }
    }

    // ========== Zip3 Publisher ==========

    /**
     * 세 Publisher를 조합하는 Publisher.
     */
    private static class Zip3Publisher<T1, T2, T3, R> implements Publisher<R> {

        private final Publisher<T1> source1;
        private final Publisher<T2> source2;
        private final Publisher<T3> source3;
        private final TriFunction<? super T1, ? super T2, ? super T3, ? extends R> combinator;

        Zip3Publisher(Publisher<T1> source1,
                      Publisher<T2> source2,
                      Publisher<T3> source3,
                      TriFunction<? super T1, ? super T2, ? super T3, ? extends R> combinator) {
            this.source1 = source1;
            this.source2 = source2;
            this.source3 = source3;
            this.combinator = combinator;
        }

        @Override
        public void subscribe(Subscriber<? super R> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("Rule 1.9: Subscriber must not be null");
            }

            Zip3Coordinator<T1, T2, T3, R> coordinator = new Zip3Coordinator<>(
                    subscriber, source1, source2, source3, combinator);
            subscriber.onSubscribe(coordinator);
            coordinator.subscribe();
        }
    }

    /**
     * 세 Publisher의 조합을 관리하는 Coordinator.
     */
    private static class Zip3Coordinator<T1, T2, T3, R> implements Subscription {

        private final Subscriber<? super R> downstream;
        private final Publisher<T1> source1;
        private final Publisher<T2> source2;
        private final Publisher<T3> source3;
        private final TriFunction<? super T1, ? super T2, ? super T3, ? extends R> combinator;

        private final AtomicReference<T1> value1 = new AtomicReference<>();
        private final AtomicReference<T2> value2 = new AtomicReference<>();
        private final AtomicReference<T3> value3 = new AtomicReference<>();

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final AtomicBoolean requested = new AtomicBoolean(false);

        private volatile Subscription subscription1;
        private volatile Subscription subscription2;
        private volatile Subscription subscription3;

        Zip3Coordinator(Subscriber<? super R> downstream,
                        Publisher<T1> source1,
                        Publisher<T2> source2,
                        Publisher<T3> source3,
                        TriFunction<? super T1, ? super T2, ? super T3, ? extends R> combinator) {
            this.downstream = downstream;
            this.source1 = source1;
            this.source2 = source2;
            this.source3 = source3;
            this.combinator = combinator;
        }

        void subscribe() {
            source1.subscribe(new Inner3Subscriber<>(this, 1));
            source2.subscribe(new Inner3Subscriber<>(this, 2));
            source3.subscribe(new Inner3Subscriber<>(this, 3));
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                if (done.compareAndSet(false, true)) {
                    downstream.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
                return;
            }

            if (requested.compareAndSet(false, true)) {
                Subscription s1 = subscription1;
                Subscription s2 = subscription2;
                Subscription s3 = subscription3;
                if (s1 != null) {
                    s1.request(Long.MAX_VALUE);
                }
                if (s2 != null) {
                    s2.request(Long.MAX_VALUE);
                }
                if (s3 != null) {
                    s3.request(Long.MAX_VALUE);
                }
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Subscription s1 = subscription1;
                Subscription s2 = subscription2;
                Subscription s3 = subscription3;
                if (s1 != null) {
                    s1.cancel();
                }
                if (s2 != null) {
                    s2.cancel();
                }
                if (s3 != null) {
                    s3.cancel();
                }
            }
        }

        @SuppressWarnings("unchecked")
        void onNext(Object value, int index) {
            if (cancelled.get() || done.get()) {
                return;
            }

            switch (index) {
                case 1 -> value1.set((T1) value);
                case 2 -> value2.set((T2) value);
                case 3 -> value3.set((T3) value);
            }

            tryComplete();
        }

        void onError(Throwable t) {
            if (cancelled.get()) {
                return;
            }

            cancel();
            if (done.compareAndSet(false, true)) {
                downstream.onError(t);
            }
        }

        void onComplete(int index) {
            if (cancelled.get() || done.get()) {
                return;
            }

            tryComplete();
        }

        private void tryComplete() {
            T1 v1 = value1.get();
            T2 v2 = value2.get();
            T3 v3 = value3.get();

            if (v1 != null && v2 != null && v3 != null && !done.get()) {
                if (done.compareAndSet(false, true)) {
                    try {
                        R result = combinator.apply(v1, v2, v3);
                        if (result == null) {
                            downstream.onError(new NullPointerException(
                                    "Rule 2.13: Combinator returned null"));
                            return;
                        }
                        if (!cancelled.get()) {
                            downstream.onNext(result);
                            downstream.onComplete();
                        }
                    } catch (Throwable t) {
                        downstream.onError(t);
                    }
                }
            }
        }

        void setSubscription(Subscription s, int index) {
            switch (index) {
                case 1 -> subscription1 = s;
                case 2 -> subscription2 = s;
                case 3 -> subscription3 = s;
            }

            if (requested.get() && !cancelled.get()) {
                s.request(Long.MAX_VALUE);
            }
        }
    }

    /**
     * Zip3용 내부 Subscriber.
     */
    private static class Inner3Subscriber<T1, T2, T3, R> implements Subscriber<Object> {

        private final Zip3Coordinator<T1, T2, T3, R> parent;
        private final int index;

        Inner3Subscriber(Zip3Coordinator<T1, T2, T3, R> parent, int index) {
            this.parent = parent;
            this.index = index;
        }

        @Override
        public void onSubscribe(Subscription s) {
            parent.setSubscription(s, index);
        }

        @Override
        public void onNext(Object item) {
            parent.onNext(item, index);
        }

        @Override
        public void onError(Throwable t) {
            parent.onError(t);
        }

        @Override
        public void onComplete() {
            parent.onComplete(index);
        }
    }
}
