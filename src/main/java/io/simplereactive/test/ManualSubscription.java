package io.simplereactive.test;

import io.simplereactive.core.Subscription;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 *
 * // request 호출 히스토리 검증
 * assertThat(subscription.getRequestHistory()).containsExactly(5L, 3L, 2L);
 * }</pre>
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다.
 */
public class ManualSubscription implements Subscription {

    private final AtomicLong requested = new AtomicLong(0);
    private final List<Long> requestHistory = new CopyOnWriteArrayList<>();
    private volatile boolean cancelled = false;
    private volatile int cancelCount = 0;

    @Override
    public void request(long n) {
        if (n > 0 && !cancelled) {
            requestHistory.add(n);
            addRequest(n);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        cancelCount++;
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
     * request 호출 히스토리를 반환합니다.
     *
     * <p>각 request 호출 시 요청한 양이 순서대로 기록됩니다.
     * 예: request(5), request(3) 호출 시 → [5, 3]
     *
     * @return request 호출 히스토리 (불변)
     */
    public List<Long> getRequestHistory() {
        return Collections.unmodifiableList(requestHistory);
    }

    /**
     * request가 호출된 횟수를 반환합니다.
     *
     * @return request 호출 횟수
     */
    public int getRequestCount() {
        return requestHistory.size();
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
     * cancel이 호출된 횟수를 반환합니다.
     *
     * @return cancel 호출 횟수
     */
    public int getCancelCount() {
        return cancelCount;
    }

    /**
     * 상태를 초기화합니다.
     */
    public void reset() {
        requested.set(0);
        requestHistory.clear();
        cancelled = false;
        cancelCount = 0;
    }

    /**
     * request 양을 추가합니다 (overflow 방지).
     */
    private void addRequest(long n) {
        requested.getAndUpdate(current -> {
            if (current == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long next = current + n;
            return next < 0 ? Long.MAX_VALUE : next;
        });
    }
}
