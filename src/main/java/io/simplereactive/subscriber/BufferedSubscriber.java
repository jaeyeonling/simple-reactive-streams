package io.simplereactive.subscriber;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 버퍼를 사용하여 Backpressure를 처리하는 Subscriber.
 *
 * <p>upstream(Publisher)으로부터 받은 데이터를 버퍼에 저장하고,
 * downstream(실제 Subscriber)의 요청에 따라 전달합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * Publisher ──onNext──> BufferedSubscriber ──onNext──> Downstream Subscriber
 *                              │
 *                         [Buffer]
 *                              │
 *                   request(bufferSize) 미리 요청
 * </pre>
 *
 * <h2>오버플로우 처리</h2>
 * <p>버퍼가 가득 찬 상태에서 새 데이터가 도착하면
 * {@link OverflowStrategy}에 따라 처리합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * var downstream = new MySubscriber<>();
 * var buffered = new BufferedSubscriber<>(downstream, 100, OverflowStrategy.DROP_OLDEST);
 * publisher.subscribe(buffered);
 * }</pre>
 *
 * @param <T> 요소 타입
 * @see OverflowStrategy
 */
public class BufferedSubscriber<T> implements Subscriber<T> {

    private final Subscriber<? super T> downstream;
    private final int bufferSize;
    private final OverflowStrategy strategy;

    private final Queue<T> buffer;
    private final AtomicInteger bufferCount = new AtomicInteger(0);
    private final AtomicLong downstreamDemand = new AtomicLong(0);
    private final AtomicInteger wip = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    private volatile Subscription upstream;
    private volatile Throwable error;
    private volatile boolean upstreamCompleted = false;

    /**
     * BufferedSubscriber를 생성합니다.
     *
     * @param downstream 데이터를 전달받을 하위 Subscriber
     * @param bufferSize 버퍼 크기 (양수)
     * @param strategy 오버플로우 전략
     * @throws NullPointerException downstream 또는 strategy가 null인 경우
     * @throws IllegalArgumentException bufferSize가 0 이하인 경우
     */
    public BufferedSubscriber(
            Subscriber<? super T> downstream,
            int bufferSize,
            OverflowStrategy strategy) {
        this.downstream = Objects.requireNonNull(downstream, "Downstream must not be null");
        this.strategy = Objects.requireNonNull(strategy, "Strategy must not be null");
        
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive, but was " + bufferSize);
        }
        this.bufferSize = bufferSize;
        this.buffer = new ConcurrentLinkedQueue<>();
    }

    /**
     * 기본 전략(DROP_LATEST)으로 BufferedSubscriber를 생성합니다.
     *
     * @param downstream 데이터를 전달받을 하위 Subscriber
     * @param bufferSize 버퍼 크기
     */
    public BufferedSubscriber(Subscriber<? super T> downstream, int bufferSize) {
        this(downstream, bufferSize, OverflowStrategy.DROP_LATEST);
    }

    @Override
    public void onSubscribe(Subscription s) {
        // Rule 2.5: 이미 구독된 경우 새 Subscription을 cancel하고 에러 시그널
        if (this.upstream != null) {
            s.cancel();
            onError(new IllegalStateException(
                    "Rule 2.5: onSubscribe must not be called more than once"));
            return;
        }
        
        this.upstream = s;
        downstream.onSubscribe(new BufferedSubscription());
        
        // 버퍼 크기만큼 미리 요청 (prefetch)
        s.request(bufferSize);
    }

    @Override
    public void onNext(T item) {
        if (terminated.get() || cancelled.get()) {
            return;
        }

        // Rule 2.13: null 체크
        if (item == null) {
            upstream.cancel();
            onError(new NullPointerException(
                    "Rule 2.13: onNext must not be called with null"));
            return;
        }

        // 버퍼에 추가 시도
        if (bufferCount.get() >= bufferSize) {
            // 버퍼가 가득 참 - 전략에 따라 처리
            handleOverflow(item);
        } else {
            addToBuffer(item);
        }

        drain();
    }

    @Override
    public void onError(Throwable t) {
        if (terminated.compareAndSet(false, true)) {
            error = t;
            drain();
        }
    }

    @Override
    public void onComplete() {
        upstreamCompleted = true;
        drain();
    }

    /**
     * 버퍼가 가득 찼을 때 전략에 따라 처리합니다.
     */
    private void handleOverflow(T item) {
        switch (strategy) {
            case DROP_OLDEST:
                // 가장 오래된 것 제거 후 새 아이템 추가
                T dropped = buffer.poll();
                if (dropped != null) {
                    bufferCount.decrementAndGet();
                }
                addToBuffer(item);
                break;

            case DROP_LATEST:
                // 새 아이템 무시 (아무것도 안 함)
                break;

            case ERROR:
                // 에러 발생
                if (terminated.compareAndSet(false, true)) {
                    upstream.cancel();
                    error = new BufferOverflowException(bufferSize);
                    drain(); // 에러 즉시 전달
                }
                break;
        }
    }

    /**
     * 버퍼에 아이템을 추가합니다.
     */
    private void addToBuffer(T item) {
        buffer.offer(item);
        bufferCount.incrementAndGet();
    }

    /**
     * 버퍼에서 downstream으로 데이터를 전달합니다.
     */
    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }

        int missed = 1;
        do {
            // 에러 체크
            if (checkTerminated()) {
                return;
            }

            // demand가 있고 버퍼에 데이터가 있으면 전달
            long demand = downstreamDemand.get();
            long emitted = 0;

            while (emitted < demand) {
                if (cancelled.get()) {
                    buffer.clear();
                    return;
                }

                T item = buffer.poll();
                if (item == null) {
                    break; // 버퍼 비어있음
                }

                bufferCount.decrementAndGet();
                downstream.onNext(item);
                emitted++;

                // upstream에 추가 요청 (replenish)
                upstream.request(1);
            }

            // demand 감소
            if (emitted > 0 && downstreamDemand.get() != Long.MAX_VALUE) {
                downstreamDemand.addAndGet(-emitted);
            }

            // 완료 체크
            if (upstreamCompleted && buffer.isEmpty()) {
                if (terminated.compareAndSet(false, true)) {
                    downstream.onComplete();
                    return;
                }
            }

        } while ((missed = wip.addAndGet(-missed)) != 0);
    }

    /**
     * 종료 상태를 체크하고 에러가 있으면 전달합니다.
     */
    private boolean checkTerminated() {
        if (cancelled.get()) {
            buffer.clear();
            return true;
        }

        Throwable e = error;
        if (e != null && terminated.get()) {
            buffer.clear();
            downstream.onError(e);
            return true;
        }

        return false;
    }

    /**
     * downstream에 제공되는 Subscription.
     */
    private class BufferedSubscription implements Subscription {

        @Override
        public void request(long n) {
            if (n <= 0) {
                onError(new IllegalArgumentException(
                        "Rule 3.9: request amount must be positive, but was " + n));
                return;
            }

            // downstream demand 추가
            long current, next;
            do {
                current = downstreamDemand.get();
                if (current == Long.MAX_VALUE) {
                    return;
                }
                next = current + n;
                if (next < 0) {
                    next = Long.MAX_VALUE;
                }
            } while (!downstreamDemand.compareAndSet(current, next));

            drain();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                upstream.cancel();
                if (wip.getAndIncrement() == 0) {
                    buffer.clear();
                }
            }
        }
    }
}
