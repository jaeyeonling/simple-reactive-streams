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
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 1.1: Publisher.subscribe는 onSubscribe를 호출해야 한다</li>
 *   <li>Rule 1.9: subscribe의 인자가 null이면 NullPointerException을 던져야 한다</li>
 *   <li>Rule 2.13: Subscriber가 예외를 던지면 구독을 취소하고 onError를 호출해야 한다</li>
 *   <li>Rule 3.9: request(n)에서 n <= 0이면 onError를 호출해야 한다</li>
 * </ul>
 */
public final class RangePublisher implements Publisher<Integer> {

    private final int start;
    private final int count;

    /**
     * RangePublisher를 생성합니다.
     *
     * @param start 시작값 (포함)
     * @param count 발행할 개수
     * @throws IllegalArgumentException count가 음수이거나, start + count가 Integer.MAX_VALUE를 초과하는 경우
     */
    public RangePublisher(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must not be negative, but was " + count);
        }
        // Integer overflow 체크: start + count > Integer.MAX_VALUE
        // long으로 변환하여 안전하게 비교
        if ((long) start + count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Range overflow: start(" + start + ") + count(" + count + ") exceeds Integer.MAX_VALUE");
        }
        this.start = start;
        this.count = count;
    }

    @Override
    public void subscribe(Subscriber<? super Integer> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
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
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final AtomicInteger wip = new AtomicInteger(0);

        RangeSubscription(Subscriber<? super Integer> subscriber, int start, int count) {
            this.subscriber = subscriber;
            this.current = new AtomicInteger(start);
            this.end = start + count;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // Rule 3.9: 이미 에러/완료 상태면 중복 에러 방지
                if (done.compareAndSet(false, true)) {
                    cancelled.set(true);
                    subscriber.onError(new IllegalArgumentException(
                            "Rule 3.9: request amount must be positive, but was " + n));
                }
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
            requested.getAndUpdate(current -> {
                if (current == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                long next = current + n;
                return next < 0 ? Long.MAX_VALUE : next;
            });
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

                    // getAndIncrement로 atomic하게 값을 가져오고 증가
                    int value = current.getAndIncrement();
                    if (value >= end) {
                        // 범위를 벗어난 경우 완료
                        if (done.compareAndSet(false, true)) {
                            subscriber.onComplete();
                        }
                        return;
                    }

                    try {
                        subscriber.onNext(value);
                        emitted++;
                    } catch (Throwable t) {
                        // Rule 2.13: Subscriber가 예외를 던지면 구독 취소
                        cancelled.set(true);
                        if (done.compareAndSet(false, true)) {
                            subscriber.onError(t);
                        }
                        return;
                    }
                }

                if (emitted > 0 && requested.get() != Long.MAX_VALUE) {
                    requested.addAndGet(-emitted);
                }
            } while ((missed = wip.addAndGet(-missed)) != 0);
        }
    }
}
