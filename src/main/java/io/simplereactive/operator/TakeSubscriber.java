package io.simplereactive.operator;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Take를 수행하는 Subscriber.
 *
 * <p>지정된 개수만큼 요소를 전달한 후 upstream을 cancel하고
 * downstream에 onComplete를 보냅니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * upstream.onNext(T) → count++
 *                         │
 *              ┌──────────┴──────────┐
 *              │                     │
 *         count < n            count >= n
 *              │                     │
 *        onNext(T)            onNext(T)
 *              │               cancel()
 *              │             onComplete()
 *              ▼                     │
 *         downstream            upstream
 * </pre>
 *
 * <h2>스레드 안전성</h2>
 * <p>AtomicLong과 AtomicBoolean을 사용하여 동시 접근에 안전합니다.
 *
 * @param <T> 요소 타입
 * @see TakeOperator
 */
final class TakeSubscriber<T> implements Subscriber<T>, Subscription {

    private final Subscriber<? super T> downstream;
    private final long limit;
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong requested = new AtomicLong(0);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private Subscription upstream;

    /**
     * TakeSubscriber를 생성합니다.
     *
     * @param downstream downstream Subscriber
     * @param limit 가져올 요소 개수
     */
    TakeSubscriber(Subscriber<? super T> downstream, long limit) {
        this.downstream = downstream;
        this.limit = limit;
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

        long c = count.incrementAndGet();
        downstream.onNext(item);

        if (c >= limit) {
            // limit에 도달하면 완료
            if (done.compareAndSet(false, true)) {
                upstream.cancel();
                downstream.onComplete();
            }
        }
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

    // ========== Subscription 구현 ==========

    @Override
    public void request(long n) {
        if (n <= 0) {
            return;
        }

        // 이미 완료되었으면 무시
        if (done.get()) {
            return;
        }

        // 남은 개수만큼만 요청 (불필요한 데이터 생성 방지)
        long currentCount = count.get();
        long remaining = limit - currentCount;

        if (remaining <= 0) {
            return;
        }

        // n과 remaining 중 작은 값만 요청
        long toRequest = Math.min(n, remaining);
        upstream.request(toRequest);
    }

    @Override
    public void cancel() {
        if (done.compareAndSet(false, true)) {
            upstream.cancel();
        }
    }
}
