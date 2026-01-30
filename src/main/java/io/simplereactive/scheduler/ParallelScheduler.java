package io.simplereactive.scheduler;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 병렬 스레드 풀에서 작업을 실행하는 Scheduler.
 *
 * <p>CPU 코어 수만큼의 스레드를 사용하여 병렬로 작업을 실행합니다.
 * CPU 집약적 작업에 적합합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                ParallelScheduler                            │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │  schedule(task1) ─┐       ┌─ [parallel-1] ─> task 실행     │
 * │  schedule(task2) ─┼─> Pool├─ [parallel-2] ─> task 실행     │
 * │  schedule(task3) ─┤       ├─ [parallel-3] ─> task 실행     │
 * │  schedule(task4) ─┘       └─ [parallel-4] ─> task 실행     │
 * │                                                             │
 * │  Main Thread              Thread Pool (CPU cores)           │
 * │  ───────────              ─────────────────────             │
 * │  submit(t1)  ──────────>  parallel-1: t1 실행              │
 * │  submit(t2)  ──────────>  parallel-2: t2 실행  (동시)      │
 * │  submit(t3)  ──────────>  parallel-3: t3 실행  (동시)      │
 * │  submit(t4)  ──────────>  parallel-4: t4 실행  (동시)      │
 * │                                                             │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>특성</h2>
 * <ul>
 *   <li>CPU 코어 수만큼 스레드 사용 (기본값)</li>
 *   <li>병렬 처리 가능 - 여러 작업이 동시에 실행</li>
 *   <li>CPU 바운드 작업에 적합</li>
 *   <li>작업 순서 보장 안 됨 (병렬 실행)</li>
 * </ul>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>publishOn에서 사용 시 각 Worker는 순차 처리 보장</li>
 *   <li>Reactive Streams 시그널 순서는 Worker 단위로 보장</li>
 *   <li>CPU 집약적 변환 작업(map)에 적합</li>
 * </ul>
 *
 * <h2>주의사항</h2>
 * <p>publishOn에서 ParallelScheduler를 사용할 때, 각 Subscriber는
 * 자신의 Worker를 사용하므로 시그널 순서가 보장됩니다.
 * 단, 여러 Subscriber 간의 실행 순서는 보장되지 않습니다.
 *
 * @see Schedulers#parallel()
 */
public final class ParallelScheduler implements Scheduler {

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private static final int DEFAULT_PARALLELISM = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executor;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final String name;
    private final int parallelism;

    /**
     * CPU 코어 수만큼의 스레드를 사용하는 ParallelScheduler를 생성합니다.
     */
    public ParallelScheduler() {
        this(DEFAULT_PARALLELISM);
    }

    /**
     * 지정된 스레드 수를 사용하는 ParallelScheduler를 생성합니다.
     *
     * @param parallelism 스레드 수
     * @throws IllegalArgumentException parallelism이 1 미만인 경우
     */
    public ParallelScheduler(int parallelism) {
        this("parallel-" + COUNTER.incrementAndGet(), parallelism);
    }

    /**
     * 지정된 이름과 스레드 수를 사용하는 ParallelScheduler를 생성합니다.
     *
     * @param name 스레드 이름 접두사
     * @param parallelism 스레드 수
     * @throws IllegalArgumentException parallelism이 1 미만인 경우
     */
    public ParallelScheduler(String name, int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1, but was " + parallelism);
        }
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.parallelism = parallelism;
        
        AtomicLong threadCounter = new AtomicLong(0);
        this.executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, name + "-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 스레드 풀에서 작업을 비동기로 실행합니다.
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
     * 새로운 ParallelWorker를 생성합니다.
     *
     * <p>각 Worker는 순차적으로 작업을 실행하여
     * Reactive Streams 시그널 순서를 보장합니다.
     *
     * @return ParallelWorker 인스턴스
     */
    @Override
    public Worker createWorker() {
        return new ParallelWorker();
    }

    /**
     * 스레드 풀을 종료하고 리소스를 해제합니다.
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

    /**
     * 병렬 처리에 사용되는 스레드 수를 반환합니다.
     *
     * @return 스레드 수
     */
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public String toString() {
        return "ParallelScheduler[" + name + ", parallelism=" + parallelism + "]";
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
     * 병렬 스레드 풀 Worker.
     *
     * <p>Worker 내에서 스케줄된 작업들은 순차적으로 실행됩니다.
     */
    private final class ParallelWorker implements Worker {
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
