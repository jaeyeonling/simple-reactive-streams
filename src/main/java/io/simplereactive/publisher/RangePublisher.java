package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 정수 범위를 발행하는 Publisher.
 *
 * <p>start부터 시작하여 count개의 연속된 정수를 발행합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * range(1, 5): ──1──2──3──4──5──|
 * range(10, 3): ──10──11──12──|
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Publisher<Integer> range = new RangePublisher(1, 5);
 * range.subscribe(subscriber);
 * subscriber.request(3);  // 1, 2, 3 발행
 * subscriber.request(10); // 4, 5 발행 후 onComplete
 * }</pre>
 *
 * <h2>Backpressure</h2>
 * <p>request(n)만큼만 발행하며, 모든 요소 발행 후 onComplete를 호출합니다.
 */
public final class RangePublisher implements Publisher<Integer> {

    private final int start;
    private final int count;

    /**
     * RangePublisher를 생성합니다.
     *
     * @param start 시작값 (포함)
     * @param count 발행할 개수
     * @throws IllegalArgumentException count가 음수인 경우
     */
    public RangePublisher(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must not be negative, but was " + count);
        }
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("Subscriber must not be null");
        }
        subscriber.onSubscribe(new RangeSubscription(subscriber, start, count));
    }

    /**
     * Range 발행을 관리하는 Subscription.
     *
     * <p>WIP 패턴을 사용하여 동시 drain() 호출을 직렬화합니다.
     */
    private static final class RangeSubscription implements Subscription {

        private final Subscriber<? super Integer> subscriber;
        private final int end;

        private final AtomicInteger current;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicInteger wip = new AtomicInteger(0);

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
}
