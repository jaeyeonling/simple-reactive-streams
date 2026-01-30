package io.simplereactive.scheduler;

/**
 * 작업 실행 컨텍스트를 추상화하는 인터페이스.
 *
 * <p>Scheduler는 Reactive Streams에서 작업이 실행되는 스레드를 제어합니다.
 * subscribeOn과 publishOn 연산자와 함께 사용하여 비동기 처리를 구현합니다.
 *
 * <h2>Scheduler의 역할</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                    Scheduler                            │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                         │
 * │  Publisher ──subscribe──> [Scheduler가 스레드 결정]     │
 * │                                │                        │
 * │                          onSubscribe                    │
 * │                          onNext                         │
 * │                          onError                        │
 * │                          onComplete                     │
 * │                                │                        │
 * │                                ▼                        │
 * │                           Subscriber                    │
 * │                                                         │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Flux.range(1, 5)
 *     .subscribeOn(Schedulers.single())   // 구독 시점부터 단일 스레드에서 실행
 *     .map(x -> x * 2)
 *     .publishOn(Schedulers.parallel())   // 이후 처리는 병렬 스레드에서
 *     .subscribe(System.out::println);
 * }</pre>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>Reactive Streams 자체는 스레드에 대해 정의하지 않음</li>
 *   <li>Scheduler는 실행 컨텍스트를 추상화하여 유연성 제공</li>
 *   <li>subscribeOn: 구독 시점의 스레드 결정</li>
 *   <li>publishOn: 데이터 발행 시점의 스레드 결정</li>
 * </ul>
 *
 * @see Schedulers
 * @see Worker
 */
public interface Scheduler {

    /**
     * 작업을 스케줄링하여 실행합니다.
     *
     * <p>작업은 Scheduler의 구현에 따라 동기 또는 비동기로 실행될 수 있습니다.
     *
     * @param task 실행할 작업
     * @return 작업 취소를 위한 Disposable
     * @throws NullPointerException task가 null인 경우
     */
    Disposable schedule(Runnable task);

    /**
     * 새로운 Worker를 생성합니다.
     *
     * <p>Worker는 작업 실행을 위한 독립적인 실행 컨텍스트입니다.
     * Worker 내에서 스케줄된 작업들은 순차적으로 실행됩니다.
     *
     * @return 새로운 Worker 인스턴스
     * @see Worker
     */
    Worker createWorker();

    /**
     * Scheduler가 사용하는 리소스를 해제합니다.
     *
     * <p>스레드 풀을 사용하는 Scheduler의 경우 스레드를 종료합니다.
     * dispose() 호출 후에는 더 이상 작업을 스케줄할 수 없습니다.
     */
    void dispose();

    /**
     * Scheduler가 dispose 되었는지 확인합니다.
     *
     * @return dispose 되었으면 true
     */
    boolean isDisposed();
}
