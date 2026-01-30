package io.simplereactive.scheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 현재 스레드에서 즉시 작업을 실행하는 Scheduler.
 *
 * <p>비동기 처리 없이 호출 스레드에서 바로 실행됩니다.
 * 테스트나 동기 처리가 필요한 경우에 유용합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │              ImmediateScheduler                         │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                         │
 * │  schedule(task) ──> task.run()  (즉시, 동일 스레드)     │
 * │                                                         │
 * │  Thread: [main] ────────────────────────────────>       │
 * │                   │                                     │
 * │                task 실행                                │
 * │                   │                                     │
 * │                   ▼                                     │
 * │             schedule 반환                               │
 * │                                                         │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>특성</h2>
 * <ul>
 *   <li>스레드 전환 없음 - 호출 스레드에서 직접 실행</li>
 *   <li>스레드 풀 리소스 사용 안 함</li>
 *   <li>dispose() 호출해도 아무 영향 없음 (리소스 없음)</li>
 *   <li>테스트에서 동기적 동작 검증에 유용</li>
 * </ul>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Scheduler scheduler = Schedulers.immediate();
 *
 * // 현재 스레드에서 즉시 실행
 * scheduler.schedule(() -> {
 *     System.out.println("Thread: " + Thread.currentThread().getName());
 *     // 출력: Thread: main (또는 호출한 스레드)
 * });
 * }</pre>
 *
 * @see Schedulers#immediate()
 */
public final class ImmediateScheduler implements Scheduler {

    /**
     * 현재 스레드에서 작업을 즉시 실행합니다.
     *
     * <p>작업이 완료된 후에 반환됩니다 (동기 실행).
     *
     * @param task 실행할 작업
     * @return 이미 완료된 Disposable (작업이 즉시 실행되므로)
     * @throws NullPointerException task가 null인 경우
     */
    @Override
    public Disposable schedule(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        task.run();
        return Disposable.DISPOSED;
    }

    /**
     * 새로운 ImmediateWorker를 생성합니다.
     *
     * @return ImmediateWorker 인스턴스
     */
    @Override
    public Worker createWorker() {
        return new ImmediateWorker();
    }

    /**
     * ImmediateScheduler는 리소스를 사용하지 않으므로 아무 작업도 하지 않습니다.
     */
    @Override
    public void dispose() {
        // 리소스 없음
    }

    /**
     * ImmediateScheduler는 dispose되지 않습니다.
     *
     * @return 항상 false
     */
    @Override
    public boolean isDisposed() {
        return false;
    }

    @Override
    public String toString() {
        return "ImmediateScheduler";
    }

    /**
     * 현재 스레드에서 즉시 작업을 실행하는 Worker.
     *
     * <p>다른 Worker들과 동일하게 AtomicBoolean을 사용하여 일관성을 유지합니다.
     */
    private static final class ImmediateWorker implements Worker {

        private final AtomicBoolean disposed = new AtomicBoolean(false);

        @Override
        public Disposable schedule(Runnable task) {
            Objects.requireNonNull(task, "Task must not be null");
            
            if (disposed.get()) {
                return Disposable.DISPOSED;
            }
            
            task.run();
            return Disposable.DISPOSED;
        }

        @Override
        public void dispose() {
            disposed.set(true);
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
