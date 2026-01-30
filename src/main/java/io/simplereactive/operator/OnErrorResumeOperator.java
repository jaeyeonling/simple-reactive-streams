package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 에러 발생 시 대체 Publisher로 전환하는 Operator.
 *
 * <p>upstream에서 에러가 발생하면 fallback 함수를 호출하여
 * 대체 Publisher를 구독하고 데이터를 계속 전달합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * upstream:   ──1──2──✗
 *                     │
 *              onErrorResume(e -> fallback)
 *                     │
 * fallback:          ──3──4──|
 *                     │
 * downstream: ──1──2──3──4──|
 * </pre>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────┐
 * │             OnErrorResumeOperator                   │
 * ├─────────────────────────────────────────────────────┤
 * │                                                     │
 * │  upstream ──onNext──> downstream                   │
 * │                                                     │
 * │  upstream ──onError──> [fallback.apply(error)]     │
 * │                              │                     │
 * │                         fallbackPublisher          │
 * │                              │                     │
 * │                         onNext ──> downstream      │
 * │                                                     │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 1.4: 에러 발생 시 onError를 시그널해야 한다</li>
 *   <li>Rule 1.7: onError 후에는 어떤 시그널도 발생하면 안 된다</li>
 * </ul>
 *
 * @param <T> 요소 타입
 */
public final class OnErrorResumeOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final Function<? super Throwable, ? extends Publisher<T>> fallback;

    /**
     * OnErrorResumeOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param fallback 에러 발생 시 대체 Publisher를 반환하는 함수
     * @throws NullPointerException upstream 또는 fallback이 null인 경우
     */
    public OnErrorResumeOperator(
            Publisher<T> upstream,
            Function<? super Throwable, ? extends Publisher<T>> fallback) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.fallback = Objects.requireNonNull(fallback, "Fallback must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(new OnErrorResumeSubscriber<>(subscriber, fallback));
    }

    /**
     * OnErrorResume을 수행하는 Subscriber.
     */
    private static final class OnErrorResumeSubscriber<T> implements Subscriber<T>, Subscription {

        private final Subscriber<? super T> downstream;
        private final Function<? super Throwable, ? extends Publisher<T>> fallback;
        
        private final AtomicReference<Subscription> upstream = new AtomicReference<>();
        private final AtomicBoolean done = new AtomicBoolean(false);
        
        /** downstream이 요청한 총량에서 전달한 양을 뺀 남은 demand */
        private final AtomicLong requested = new AtomicLong(0);
        /** upstream에서 전달받아 downstream에 전달한 양 */
        private final AtomicLong emitted = new AtomicLong(0);

        OnErrorResumeSubscriber(
                Subscriber<? super T> downstream,
                Function<? super Throwable, ? extends Publisher<T>> fallback) {
            this.downstream = downstream;
            this.fallback = fallback;
        }

        // ========== Subscriber 구현 ==========

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream.compareAndSet(null, s)) {
                downstream.onSubscribe(this);
            } else {
                s.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            if (done.get()) {
                return;
            }
            emitted.incrementAndGet();
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            if (done.get()) {
                return;
            }

            // fallback Publisher로 전환
            Publisher<T> fallbackPublisher;
            try {
                fallbackPublisher = fallback.apply(t);
            } catch (Throwable ex) {
                // fallback 함수에서 예외 발생 시 원래 에러와 함께 전달
                ex.addSuppressed(t);
                if (done.compareAndSet(false, true)) {
                    downstream.onError(ex);
                }
                return;
            }

            if (fallbackPublisher == null) {
                if (done.compareAndSet(false, true)) {
                    downstream.onError(new NullPointerException(
                            "Fallback returned null for error: " + t));
                }
                return;
            }

            // 남은 demand 계산
            long remainingDemand = requested.get() - emitted.get();
            
            // fallback으로 전환
            fallbackPublisher.subscribe(new FallbackSubscriber<>(downstream, done, upstream, remainingDemand));
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
            if (n <= 0) {
                return;
            }
            // demand 추적 (overflow 방지)
            requested.getAndUpdate(current -> {
                long next = current + n;
                return next < 0 ? Long.MAX_VALUE : next;
            });
            
            Subscription s = upstream.get();
            if (s != null) {
                s.request(n);
            }
        }

        @Override
        public void cancel() {
            if (done.compareAndSet(false, true)) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
            }
        }
    }

    /**
     * Fallback Publisher를 구독하는 Subscriber.
     * 
     * <p>에러 발생 전까지의 남은 demand를 추적하여 fallback에 요청합니다.
     */
    private static final class FallbackSubscriber<T> implements Subscriber<T> {

        private final Subscriber<? super T> downstream;
        private final AtomicBoolean done;
        private final AtomicReference<Subscription> subscriptionRef;
        private final long initialDemand;

        FallbackSubscriber(
                Subscriber<? super T> downstream,
                AtomicBoolean done,
                AtomicReference<Subscription> subscriptionRef,
                long initialDemand) {
            this.downstream = downstream;
            this.done = done;
            this.subscriptionRef = subscriptionRef;
            this.initialDemand = initialDemand;
        }

        @Override
        public void onSubscribe(Subscription s) {
            // 기존 upstream을 fallback subscription으로 교체
            subscriptionRef.set(s);
            // 남은 demand만큼 요청
            if (initialDemand > 0) {
                s.request(initialDemand);
            }
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
            if (done.compareAndSet(false, true)) {
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                downstream.onComplete();
            }
        }
    }
}
