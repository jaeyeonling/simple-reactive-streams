package io.simplereactive.scheduler;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 단일 스레드에서 작업을 실행하는 Scheduler.
 *
 * <p>모든 작업이 하나의 스레드에서 순차적으로 실행됩니다.
 * 순서 보장이 필요하거나 I/O 작업에 적합합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │             SingleThreadScheduler                       │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                         │
 * │  schedule(task1) ─┐                                     │
 * │  schedule(task2) ─┼─> Queue ─> [single-1] ─> 순차 실행  │
 * │  schedule(task3) ─┘                                     │
 * │                                                         │
 * │  Main Thread      Single Thread                         │
 * │  ───────────      ─────────────                         │
 * │  submit(t1)  ───> │                                     │
 * │  submit(t2)  ───> │ t1 실행                             │
 * │  submit(t3)  ───> │ t2 실행                             │
 * │                   │ t3 실행                             │
 * │                                                         │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>특성</h2>
 * <ul>
 *   <li>단일 스레드 사용 - 모든 작업이 순차적으로 실행</li>
 *   <li>작업 순서 보장 (FIFO)</li>
 *   <li>I/O 바운드 작업에 적합</li>
 *   <li>dispose() 시 스레드 종료</li>
 * </ul>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>Reactive Streams의 시그널 순서 보장에 중요</li>
 *   <li>onNext는 반드시 순서대로 호출되어야 함</li>
 *   <li>publishOn에서 사용 시 순차 처리 보장</li>
 * </ul>
 *
 * @see Schedulers#single()
 */
public final class SingleThreadScheduler implements Scheduler {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    private final ExecutorService executor;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final String name;

    /**
     * 기본 이름을 가진 SingleThreadScheduler를 생성합니다.
     */
    public SingleThreadScheduler() {
        this("single-" + COUNTER.incrementAndGet());
    }

    /**
     * 지정된 이름을 가진 SingleThreadScheduler를 생성합니다.
     *
     * @param name 스레드 이름 접두사
     */
    public SingleThreadScheduler(String name) {
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);  // 데몬 스레드로 설정하여 JVM 종료 방해 안 함
            return t;
        });
    }

    /**
     * 단일 스레드에서 작업을 비동기로 실행합니다.
     *
     * @param task 실행할 작업
     * @return 작업 취소를 위한 Disposable
     * @throws NullPointerException task가 null인 경우
     */
    @Override
    public Disposable schedule(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");

        if (disposed.get()) {
            return Disposable.DISPOSED;
        }

        try {
            Future<?> future = executor.submit(task);
            return new FutureDisposable(future);
        } catch (RejectedExecutionException e) {
            return Disposable.DISPOSED;
        }
    }

    /**
     * 새로운 SingleWorker를 생성합니다.
     *
     * @return SingleWorker 인스턴스
     */
    @Override
    public Worker createWorker() {
        return new SingleWorker();
    }

    /**
     * 스레드를 종료하고 리소스를 해제합니다.
     *
     * <p>대기 중인 작업은 취소됩니다.
     */
    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    @Override
    public String toString() {
        return "SingleThreadScheduler[" + name + "]";
    }

    /**
     * Future를 래핑하는 Disposable.
     */
    private static final class FutureDisposable implements Disposable {
        private final Future<?> future;

        FutureDisposable(Future<?> future) {
            this.future = future;
        }

        @Override
        public void dispose() {
            future.cancel(false);
        }

        @Override
        public boolean isDisposed() {
            return future.isDone() || future.isCancelled();
        }
    }

    /**
     * 단일 스레드 Worker.
     */
    private final class SingleWorker implements Worker {
        private final AtomicBoolean workerDisposed = new AtomicBoolean(false);

        @Override
        public Disposable schedule(Runnable task) {
            Objects.requireNonNull(task, "Task must not be null");

            if (workerDisposed.get() || disposed.get()) {
                return Disposable.DISPOSED;
            }

            try {
                Future<?> future = executor.submit(task);
                return new FutureDisposable(future);
            } catch (RejectedExecutionException e) {
                return Disposable.DISPOSED;
            }
        }

        @Override
        public void dispose() {
            workerDisposed.set(true);
        }

        @Override
        public boolean isDisposed() {
            return workerDisposed.get();
        }
    }
}
