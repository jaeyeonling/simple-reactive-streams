package io.simplereactive.subscription;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subscription의 공통 기능을 제공하는 추상 클래스.
 *
 * <p>demand 관리, 취소 처리, 규약 검증 등 모든 Subscription에서
 * 필요한 기본 기능을 구현합니다.
 *
 * <h2>사용 방법</h2>
 * <pre>{@code
 * class MySubscription extends BaseSubscription<String> {
 *     MySubscription(Subscriber<? super String> subscriber) {
 *         super(subscriber);
 *     }
 *
 *     @Override
 *     protected void onRequest() {
 *         while (hasDemand() && hasMoreData()) {
 *             emit(nextData());
 *         }
 *         if (!hasMoreData()) {
 *             complete();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다. demand와 cancelled 상태는
 * Atomic 연산으로 관리됩니다.
 *
 * @param <T> 발행할 요소의 타입
 */
public abstract class BaseSubscription<T> implements Subscription {

    protected final Subscriber<? super T> subscriber;

    private final AtomicLong requested = new AtomicLong(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /**
     * BaseSubscription을 생성합니다.
     *
     * @param subscriber 데이터를 받을 Subscriber
     */
    protected BaseSubscription(Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * {@inheritDoc}
     *
     * <p>n개의 데이터를 요청합니다. 요청량은 누적되며,
     * {@link #onRequest()}를 호출하여 데이터 발행을 시작합니다.
     *
     * @throws IllegalArgumentException Rule 3.9 - n이 0 이하인 경우 (onError로 전달)
     */
    @Override
    public final void request(long n) {
        // Rule 3.9: n <= 0이면 에러
        if (n <= 0) {
            signalError(new IllegalArgumentException(
                    "Rule 3.9: request amount must be positive, but was " + n));
            return;
        }

        if (isCancelled() || isCompleted()) {
            return;
        }

        addDemand(n);
        onRequest();
    }

    /**
     * {@inheritDoc}
     *
     * <p>구독을 취소합니다. 취소 후에는 더 이상 시그널이 발생하지 않습니다.
     * 이 메서드는 멱등성을 가집니다 (여러 번 호출해도 안전).
     */
    @Override
    public final void cancel() {
        // Rule 3.5: cancel은 멱등성을 가져야 함
        cancelled.set(true);
    }

    /**
     * request가 호출되었을 때 실행될 로직을 구현합니다.
     *
     * <p>하위 클래스는 이 메서드에서 {@link #hasDemand()}, {@link #emit(Object)},
     * {@link #complete()} 등을 사용하여 데이터를 발행해야 합니다.
     */
    protected abstract void onRequest();

    // ========== 하위 클래스용 유틸리티 메서드 ==========

    /**
     * 현재 요청된 demand가 있는지 확인합니다.
     *
     * @return demand가 0보다 크면 true
     */
    protected final boolean hasDemand() {
        return requested.get() > 0;
    }

    /**
     * 현재 demand를 반환합니다.
     *
     * @return 현재 요청량
     */
    protected final long getDemand() {
        return requested.get();
    }

    /**
     * 구독이 취소되었는지 확인합니다.
     *
     * @return 취소되었으면 true
     */
    protected final boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 완료되었는지 확인합니다.
     *
     * @return 완료되었으면 true
     */
    protected final boolean isCompleted() {
        return completed.get();
    }

    /**
     * 데이터를 발행합니다.
     *
     * <p>취소되었거나 완료된 경우 무시됩니다.
     * null을 발행하면 onError가 호출됩니다.
     *
     * @param item 발행할 데이터
     * @return 발행 성공 시 true, 취소/완료/에러 시 false
     */
    protected final boolean emit(T item) {
        if (isCancelled() || isCompleted()) {
            return false;
        }

        // Rule 2.13: null 체크
        if (item == null) {
            signalError(new NullPointerException(
                    "Rule 2.13: onNext must not be called with null"));
            return false;
        }

        subscriber.onNext(item);
        decrementDemand();
        return true;
    }

    /**
     * 스트림을 완료합니다.
     *
     * <p>취소되었거나 이미 완료된 경우 무시됩니다.
     * 한 번만 호출됩니다.
     */
    protected final void complete() {
        if (isCancelled()) {
            return;
        }
        if (completed.compareAndSet(false, true)) {
            subscriber.onComplete();
        }
    }

    /**
     * 에러를 발생시킵니다.
     *
     * <p>취소되었거나 이미 완료된 경우 무시됩니다.
     *
     * @param error 발생한 에러
     */
    protected final void signalError(Throwable error) {
        if (isCancelled()) {
            return;
        }
        if (completed.compareAndSet(false, true)) {
            subscriber.onError(error);
        }
    }

    // ========== private 메서드 ==========

    /**
     * demand를 추가합니다 (overflow 방지).
     */
    private void addDemand(long n) {
        long current, next;
        do {
            current = requested.get();
            if (current == Long.MAX_VALUE) {
                return; // 이미 unbounded
            }
            next = current + n;
            if (next < 0) {
                next = Long.MAX_VALUE; // overflow → unbounded
            }
        } while (!requested.compareAndSet(current, next));
    }

    /**
     * demand를 1 감소시킵니다.
     */
    private void decrementDemand() {
        if (requested.get() != Long.MAX_VALUE) {
            requested.decrementAndGet();
        }
    }
}
