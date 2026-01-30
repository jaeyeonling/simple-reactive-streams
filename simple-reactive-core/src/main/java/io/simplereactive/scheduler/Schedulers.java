package io.simplereactive.scheduler;

import java.util.function.Supplier;

/**
 * 다양한 Scheduler 인스턴스를 제공하는 팩토리 클래스.
 *
 * <h2>제공하는 Scheduler</h2>
 * <ul>
 *   <li>{@link #immediate()} - 현재 스레드에서 즉시 실행</li>
 *   <li>{@link #single()} - 단일 스레드에서 순차 실행</li>
 *   <li>{@link #parallel()} - 병렬 스레드 풀에서 실행</li>
 * </ul>
 *
 * <h2>Scheduler 선택 가이드</h2>
 * <pre>
 * ┌─────────────────┬──────────────────────────────────────────┐
 * │   Scheduler     │              사용 사례                   │
 * ├─────────────────┼──────────────────────────────────────────┤
 * │ immediate()     │ 테스트, 동기 처리, 디버깅                │
 * │ single()        │ 순차 처리, I/O 작업, 이벤트 루프         │
 * │ parallel()      │ CPU 집약적 작업, 병렬 처리               │
 * └─────────────────┴──────────────────────────────────────────┘
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // 구독 시점의 스레드 변경
 * Flux.range(1, 10)
 *     .subscribeOn(Schedulers.single())
 *     .subscribe(System.out::println);
 *
 * // 발행 시점의 스레드 변경
 * Flux.range(1, 10)
 *     .publishOn(Schedulers.parallel())
 *     .map(x -> heavyComputation(x))
 *     .subscribe(System.out::println);
 * }</pre>
 */
public final class Schedulers {

    private Schedulers() {
        // 유틸리티 클래스
    }

    // ========== 싱글톤 인스턴스 ==========

    // Immediate: eager 초기화 - 리소스(스레드) 없음, 가볍고 항상 사용됨
    private static final Scheduler IMMEDIATE = new ImmediateScheduler();

    // Single/Parallel: lazy 초기화 - 스레드 생성 비용이 있어 실제 사용 시점에 초기화
    private static final CachedScheduler SINGLE = new CachedScheduler(SingleThreadScheduler::new);
    private static final CachedScheduler PARALLEL = new CachedScheduler(ParallelScheduler::new);

    // ========== 공유 인스턴스 접근 ==========

    /**
     * 현재 스레드에서 즉시 작업을 실행하는 Scheduler를 반환합니다.
     *
     * <p>비동기 처리 없이 호출 스레드에서 바로 실행됩니다.
     * 테스트나 동기 처리가 필요한 경우에 유용합니다.
     *
     * <h3>특성</h3>
     * <ul>
     *   <li>스레드 전환 없음</li>
     *   <li>호출 스레드에서 직접 실행</li>
     *   <li>스레드 풀 리소스 사용 안 함</li>
     * </ul>
     *
     * @return ImmediateScheduler 인스턴스
     */
    public static Scheduler immediate() {
        return IMMEDIATE;
    }

    /**
     * 단일 스레드에서 작업을 실행하는 Scheduler를 반환합니다.
     *
     * <p>모든 작업이 하나의 스레드에서 순차적으로 실행됩니다.
     * 순서 보장이 필요하거나 I/O 작업에 적합합니다.
     *
     * <h3>특성</h3>
     * <ul>
     *   <li>단일 스레드 사용</li>
     *   <li>작업 순서 보장 (FIFO)</li>
     *   <li>I/O 바운드 작업에 적합</li>
     * </ul>
     *
     * @return SingleThreadScheduler 인스턴스
     */
    public static Scheduler single() {
        return SINGLE.get();
    }

    /**
     * 병렬 스레드 풀에서 작업을 실행하는 Scheduler를 반환합니다.
     *
     * <p>CPU 코어 수만큼의 스레드를 사용하여 병렬로 작업을 실행합니다.
     * CPU 집약적 작업에 적합합니다.
     *
     * <h3>특성</h3>
     * <ul>
     *   <li>CPU 코어 수만큼 스레드 사용</li>
     *   <li>병렬 처리 가능</li>
     *   <li>CPU 바운드 작업에 적합</li>
     * </ul>
     *
     * @return ParallelScheduler 인스턴스
     */
    public static Scheduler parallel() {
        return PARALLEL.get();
    }

    // ========== 새 인스턴스 생성 ==========

    /**
     * 새로운 단일 스레드 Scheduler를 생성합니다.
     *
     * <p>공유 인스턴스가 아닌 독립적인 스레드를 사용하는 Scheduler가 필요할 때 사용합니다.
     * 사용 후 반드시 {@link Scheduler#dispose()}를 호출하여 리소스를 해제해야 합니다.
     *
     * @return 새로운 SingleThreadScheduler 인스턴스
     */
    public static Scheduler newSingle() {
        return new SingleThreadScheduler();
    }

    /**
     * 새로운 병렬 Scheduler를 생성합니다.
     *
     * <p>공유 인스턴스가 아닌 독립적인 스레드 풀을 사용하는 Scheduler가 필요할 때 사용합니다.
     * 사용 후 반드시 {@link Scheduler#dispose()}를 호출하여 리소스를 해제해야 합니다.
     *
     * @return 새로운 ParallelScheduler 인스턴스
     */
    public static Scheduler newParallel() {
        return new ParallelScheduler();
    }

    /**
     * 지정된 스레드 수를 가진 병렬 Scheduler를 생성합니다.
     *
     * @param parallelism 스레드 수
     * @return 새로운 ParallelScheduler 인스턴스
     * @throws IllegalArgumentException parallelism이 1 미만인 경우
     */
    public static Scheduler newParallel(int parallelism) {
        return new ParallelScheduler(parallelism);
    }

    // ========== 리소스 정리 ==========

    /**
     * 모든 공유 Scheduler를 dispose합니다.
     *
     * <p>애플리케이션 종료 시 호출하여 리소스를 정리합니다.
     * 테스트 후 정리 용도로도 사용할 수 있습니다.
     */
    public static void shutdownAll() {
        SINGLE.dispose();
        PARALLEL.dispose();
    }

    // ========== 내부 헬퍼 ==========

    /**
     * 스레드 안전한 lazy 초기화와 dispose를 지원하는 Scheduler 캐시.
     *
     * <p>DCL(Double-Checked Locking) 패턴을 캡슐화하여 중복 코드를 제거합니다.
     */
    private static final class CachedScheduler {
        private final Supplier<Scheduler> factory;
        private volatile Scheduler instance;

        CachedScheduler(Supplier<Scheduler> factory) {
            this.factory = factory;
        }

        Scheduler get() {
            Scheduler s = instance;
            if (s == null) {
                synchronized (this) {
                    s = instance;
                    if (s == null) {
                        s = factory.get();
                        instance = s;
                    }
                }
            }
            return s;
        }

        void dispose() {
            Scheduler s = instance;
            if (s != null) {
                synchronized (this) {
                    s = instance;
                    if (s != null) {
                        s.dispose();
                        instance = null;
                    }
                }
            }
        }
    }
}
