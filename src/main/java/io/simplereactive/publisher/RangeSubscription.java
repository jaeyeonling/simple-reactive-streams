package io.simplereactive.publisher;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Range 발행을 관리하는 Subscription.
 *
 * <p>start부터 end까지의 정수를 backpressure에 맞춰 발행합니다.
 * WIP 패턴을 사용하여 동시 drain() 호출을 직렬화합니다.
 *
 * <h2>스레드 안전성</h2>
 * <ul>
 *   <li>AtomicInteger(current): 현재 발행할 값</li>
 *   <li>AtomicLong(requested): 요청된 개수</li>
 *   <li>AtomicBoolean(cancelled): 취소 상태</li>
 *   <li>AtomicInteger(wip): WIP 패턴으로 동시성 제어</li>
 * </ul>
 *
 * <h2>Reactive Streams 규약</h2>
 * <ul>
 *   <li>Rule 3.9: request(n)에서 n <= 0이면 onError 호출</li>
 * </ul>
 *
 * @see RangePublisher
 */
final class RangeSubscription implements Subscription {

    private final Subscriber<? super Integer> subscriber;
    private final int end;

    private final AtomicInteger current;
    private final AtomicLong requested = new AtomicLong(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger wip = new AtomicInteger(0);

    /**
     * RangeSubscription을 생성합니다.
     *
     * @param subscriber downstream Subscriber
     * @param start 시작값 (포함)
     * @param count 발행할 개수
     */
    RangeSubscription(Subscriber<? super Integer> subscriber, int start, int count) {
        this.subscriber = subscriber;
        this.current = new AtomicInteger(start);
        this.end = start + count;
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            cancel();
            subscriber.onError(new IllegalArgumentException(
                    "Rule 3.9: request amount must be positive, but was " + n));
            return;
        }

        addRequest(n);
        drain();
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * 요청량을 안전하게 추가합니다.
     * 오버플로우 시 Long.MAX_VALUE로 설정합니다.
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

    /**
     * WIP 패턴을 사용하여 요소를 발행합니다.
     * 동시 호출 시 하나의 스레드만 실제 발행을 수행합니다.
     */
    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }

        int missed = 1;
        do {
            long r = requested.get();
            long emitted = 0;

            while (emitted < r) {
                if (cancelled.get()) {
                    return;
                }

                int value = current.get();
                if (value >= end) {
                    subscriber.onComplete();
                    return;
                }

                subscriber.onNext(value);
                current.incrementAndGet();
                emitted++;
            }

            if (emitted > 0 && requested.get() != Long.MAX_VALUE) {
                requested.addAndGet(-emitted);
            }
        } while ((missed = wip.addAndGet(-missed)) != 0);
    }
}
