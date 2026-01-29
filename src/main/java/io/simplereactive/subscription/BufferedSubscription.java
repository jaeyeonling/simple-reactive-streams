package io.simplereactive.subscription;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.subscriber.BufferOverflowException;
import io.simplereactive.subscriber.OverflowStrategy;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 버퍼를 사용하는 Subscription 구현.
 *
 * <p>upstream으로부터 받은 데이터를 버퍼에 저장하고,
 * downstream의 요청에 따라 전달합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * upstream ──onNext──> [Buffer] ──onNext──> downstream
 *                         │
 *              request(n) 에 따라 전달
 * </pre>
 *
 * @param <T> 요소 타입
 * @see io.simplereactive.subscriber.BufferedSubscriber
 */
public class BufferedSubscription<T> implements Subscription {

    private final Subscriber<? super T> downstream;
    private final int bufferSize;
    private final OverflowStrategy strategy;
    private final Subscription upstream;

    private final Queue<T> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger bufferCount = new AtomicInteger(0);
    private final AtomicLong demand = new AtomicLong(0);
    private final AtomicInteger wip = new AtomicInteger(0);

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    private volatile Throwable error;
    private volatile boolean upstreamCompleted = false;

    /**
     * BufferedSubscription을 생성합니다.
     *
     * @param downstream downstream Subscriber
     * @param upstream upstream Subscription
     * @param bufferSize 버퍼 크기
     * @param strategy 오버플로우 전략
     */
    public BufferedSubscription(
            Subscriber<? super T> downstream,
            Subscription upstream,
            int bufferSize,
            OverflowStrategy strategy) {
        this.downstream = Objects.requireNonNull(downstream);
        this.upstream = Objects.requireNonNull(upstream);
        this.bufferSize = bufferSize;
        this.strategy = Objects.requireNonNull(strategy);
    }

    // ========== Subscription 구현 ==========

    @Override
    public void request(long n) {
        if (n <= 0) {
            signalError(new IllegalArgumentException(
                    "Rule 3.9: request amount must be positive, but was " + n));
            return;
        }

        addDemand(n);
        drain();
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            upstream.cancel();
            clearBufferIfNotDraining();
        }
    }

    // ========== 데이터 수신 메서드 (BufferedSubscriber에서 호출) ==========

    /**
     * upstream으로부터 데이터를 수신합니다.
     *
     * @param item 수신한 데이터
     */
    public void onNext(T item) {
        if (isTerminated()) {
            return;
        }

        if (item == null) {
            cancelUpstreamAndSignalError(new NullPointerException(
                    "Rule 2.13: onNext must not be called with null"));
            return;
        }

        if (isBufferFull()) {
            handleOverflow(item);
        } else {
            addToBuffer(item);
        }

        drain();
    }

    /**
     * upstream에서 에러가 발생했음을 알립니다.
     *
     * @param t 발생한 에러
     */
    public void onError(Throwable t) {
        if (terminated.compareAndSet(false, true)) {
            error = t;
            drain();
        }
    }

    /**
     * upstream이 완료되었음을 알립니다.
     */
    public void onComplete() {
        upstreamCompleted = true;
        drain();
    }

    // ========== 상태 확인 메서드 ==========

    /**
     * 취소 여부를 반환합니다.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 종료 여부를 반환합니다.
     */
    public boolean isTerminated() {
        return terminated.get() || cancelled.get();
    }

    // ========== private 메서드: 버퍼 관리 ==========

    private boolean isBufferFull() {
        return bufferCount.get() >= bufferSize;
    }

    private void addToBuffer(T item) {
        buffer.offer(item);
        bufferCount.incrementAndGet();
    }

    private T pollFromBuffer() {
        T item = buffer.poll();
        if (item != null) {
            bufferCount.decrementAndGet();
        }
        return item;
    }

    private void clearBuffer() {
        buffer.clear();
        bufferCount.set(0);
    }

    private void clearBufferIfNotDraining() {
        if (wip.getAndIncrement() == 0) {
            clearBuffer();
        }
    }

    // ========== private 메서드: 오버플로우 처리 ==========

    private void handleOverflow(T item) {
        switch (strategy) {
            case DROP_OLDEST -> dropOldestAndAdd(item);
            case DROP_LATEST -> { /* 새 아이템 무시 */ }
            case ERROR -> signalOverflowError();
        }
    }

    private void dropOldestAndAdd(T item) {
        pollFromBuffer(); // 가장 오래된 것 제거
        addToBuffer(item);
    }

    private void signalOverflowError() {
        if (terminated.compareAndSet(false, true)) {
            upstream.cancel();
            error = new BufferOverflowException(bufferSize);
            drain();
        }
    }

    // ========== private 메서드: 에러 처리 ==========

    private void signalError(Throwable t) {
        if (terminated.compareAndSet(false, true)) {
            error = t;
            drain();
        }
    }

    private void cancelUpstreamAndSignalError(Throwable t) {
        upstream.cancel();
        signalError(t);
    }

    // ========== private 메서드: demand 관리 ==========

    private void addDemand(long n) {
        long current, next;
        do {
            current = demand.get();
            if (current == Long.MAX_VALUE) {
                return;
            }
            next = current + n;
            if (next < 0) {
                next = Long.MAX_VALUE;
            }
        } while (!demand.compareAndSet(current, next));
    }

    private void decrementDemand(long n) {
        if (demand.get() != Long.MAX_VALUE) {
            demand.addAndGet(-n);
        }
    }

    private boolean hasDemand() {
        return demand.get() > 0;
    }

    // ========== private 메서드: drain 로직 ==========

    /**
     * 버퍼에서 downstream으로 데이터를 전달합니다.
     *
     * <p>WIP 패턴을 사용하여 동시 실행을 방지합니다.
     */
    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }

        int missed = 1;
        do {
            if (drainLoop()) {
                return; // 종료됨
            }
        } while ((missed = wip.addAndGet(-missed)) != 0);
    }

    /**
     * drain 루프의 본체.
     *
     * @return 종료되어야 하면 true
     */
    private boolean drainLoop() {
        // 에러 또는 취소 체크
        if (checkTerminatedWithError()) {
            return true;
        }

        // demand가 있는 만큼 버퍼에서 전달
        long emitted = emitFromBuffer();

        // demand 감소
        if (emitted > 0) {
            decrementDemand(emitted);
        }

        // 완료 체크
        if (checkCompleted()) {
            return true;
        }

        // replenish는 drain 완료 후에 수행 (재귀 방지)
        if (emitted > 0) {
            replenishUpstream(emitted);
        }

        return false;
    }

    /**
     * 버퍼에서 데이터를 꺼내 downstream으로 전달합니다.
     *
     * @return 전달한 개수
     */
    private long emitFromBuffer() {
        // 현재 demand의 스냅샷
        long currentDemand = demand.get();
        long emitted = 0;

        while (emitted < currentDemand) {
            if (cancelled.get()) {
                clearBuffer();
                return emitted;
            }

            T item = pollFromBuffer();
            if (item == null) {
                break; // 버퍼 비어있음
            }

            downstream.onNext(item);
            emitted++;
        }

        return emitted;
    }

    /**
     * upstream에 추가 요청을 보냅니다 (replenish).
     */
    private void replenishUpstream(long n) {
        if (!cancelled.get() && !upstreamCompleted) {
            upstream.request(n);
        }
    }

    /**
     * 에러 상태를 체크하고 에러가 있으면 downstream에 전달합니다.
     *
     * @return 종료되어야 하면 true
     */
    private boolean checkTerminatedWithError() {
        if (cancelled.get()) {
            clearBuffer();
            return true;
        }

        Throwable e = error;
        if (e != null && terminated.get()) {
            clearBuffer();
            downstream.onError(e);
            return true;
        }

        return false;
    }

    /**
     * 완료 상태를 체크하고 완료되었으면 downstream에 알립니다.
     *
     * @return 종료되어야 하면 true
     */
    private boolean checkCompleted() {
        if (upstreamCompleted && bufferCount.get() == 0 && !cancelled.get()) {
            if (terminated.compareAndSet(false, true)) {
                downstream.onComplete();
                return true;
            }
        }
        return false;
    }
}
