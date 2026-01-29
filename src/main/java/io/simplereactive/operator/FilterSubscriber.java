package io.simplereactive.operator;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.function.Predicate;

/**
 * Filter를 수행하는 Subscriber.
 *
 * <p>Subscription도 구현하여 downstream의 request를 중개합니다.
 * 필터링된 요소에 대해 추가 request를 보내기 위함입니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * upstream.onNext(T) → predicate.test(T)
 *                          │
 *               ┌──────────┴──────────┐
 *               │                     │
 *             true                  false
 *               │                     │
 *         onNext(T)            request(1)
 *               │               (보충 요청)
 *               ▼                     │
 *          downstream            upstream
 * </pre>
 *
 * <h2>에러 처리</h2>
 * <p>predicate에서 예외 발생 시 upstream을 cancel하고 downstream에 onError 전달
 *
 * @param <T> 요소 타입
 * @see FilterOperator
 */
final class FilterSubscriber<T> implements Subscriber<T>, Subscription {

    private final Subscriber<? super T> downstream;
    private final Predicate<? super T> predicate;
    private Subscription upstream;
    private boolean done = false;

    /**
     * FilterSubscriber를 생성합니다.
     *
     * @param downstream downstream Subscriber
     * @param predicate 필터 조건
     */
    FilterSubscriber(Subscriber<? super T> downstream, Predicate<? super T> predicate) {
        this.downstream = downstream;
        this.predicate = predicate;
    }

    // ========== Subscriber 구현 ==========

    @Override
    public void onSubscribe(Subscription s) {
        this.upstream = s;
        // 우리가 만든 Subscription(this)을 전달
        // 필터링된 요소에 대한 추가 request를 처리하기 위함
        downstream.onSubscribe(this);
    }

    @Override
    public void onNext(T item) {
        if (done) {
            return;
        }

        boolean matches;
        try {
            matches = predicate.test(item);
        } catch (Throwable t) {
            // predicate에서 예외 발생 시 에러 처리
            upstream.cancel();
            onError(t);
            return;
        }

        if (matches) {
            downstream.onNext(item);
        } else {
            // 필터링된 요소는 전달되지 않으므로 추가 요청
            // downstream이 n개 요청했으면 n개를 받아야 함
            upstream.request(1);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (done) {
            return;
        }
        done = true;
        downstream.onError(t);
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        downstream.onComplete();
    }

    // ========== Subscription 구현 ==========

    @Override
    public void request(long n) {
        upstream.request(n);
    }

    @Override
    public void cancel() {
        upstream.cancel();
    }
}
