package io.simplereactive.test;

import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 수동 Subscription 구현.
 *
 * <p>Publisher 없이 Subscriber를 테스트할 때 사용합니다.
 * request와 cancel 호출을 기록하여 검증할 수 있습니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * var subscription = new ManualSubscription();
 * subscriber.onSubscribe(subscription);
 *
 * // 검증
 * assertThat(subscription.getRequestedCount()).isEqualTo(10);
 * assertThat(subscription.isCancelled()).isFalse();
 * }</pre>
 */
public class ManualSubscription implements Subscription {

    private final AtomicLong requested = new AtomicLong(0);
    private volatile boolean cancelled = false;

    @Override
    public void request(long n) {
        if (n > 0 && !cancelled) {
            addRequest(n);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    /**
     * 총 요청된 양을 반환합니다.
     *
     * @return 요청된 총량
     */
    public long getRequestedCount() {
        return requested.get();
    }

    /**
     * 취소 여부를 반환합니다.
     *
     * @return 취소되었으면 true
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 상태를 초기화합니다.
     */
    public void reset() {
        requested.set(0);
        cancelled = false;
    }

    /**
     * request 양을 추가합니다 (overflow 방지).
     */
    private void addRequest(long n) {
        long current, next;
        do {
            current = requested.get();
            if (current == Long.MAX_VALUE) {
                return;
            }
            next = current + n;
            if (next < 0) {
                next = Long.MAX_VALUE;
            }
        } while (!requested.compareAndSet(current, next));
    }
}
