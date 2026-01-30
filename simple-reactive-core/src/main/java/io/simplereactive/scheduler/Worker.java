package io.simplereactive.scheduler;

/**
 * 작업을 순차적으로 실행하는 독립적인 실행 컨텍스트.
 *
 * <p>Worker 내에서 스케줄된 작업들은 순서가 보장됩니다.
 * 이는 Reactive Streams의 시그널 순서 보장에 중요합니다.
 *
 * <h2>Worker의 특성</h2>
 * <ul>
 *   <li>작업들은 FIFO 순서로 실행</li>
 *   <li>동일 Worker 내 작업은 동시에 실행되지 않음</li>
 *   <li>dispose 시 대기 중인 작업도 취소됨</li>
 * </ul>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Scheduler scheduler = Schedulers.parallel();
 * Worker worker = scheduler.createWorker();
 *
 * try {
 *     worker.schedule(() -> System.out.println("Task 1"));
 *     worker.schedule(() -> System.out.println("Task 2"));
 *     // Task 1이 완료된 후 Task 2 실행 (순서 보장)
 * } finally {
 *     worker.dispose();
 * }
 * }</pre>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>publishOn에서 각 Subscriber는 자신의 Worker를 가짐</li>
 *   <li>Worker 단위로 시그널 순서가 보장됨</li>
 *   <li>ParallelScheduler도 Worker 내에서는 순차 실행</li>
 * </ul>
 *
 * @see Scheduler#createWorker()
 */
public interface Worker extends Disposable {

    /**
     * Worker의 실행 컨텍스트에서 작업을 스케줄합니다.
     *
     * <p>동일 Worker에서 스케줄된 작업들은 순차적으로 실행됩니다.
     *
     * @param task 실행할 작업
     * @return 작업 취소를 위한 Disposable
     * @throws NullPointerException task가 null인 경우
     */
    Disposable schedule(Runnable task);
}
