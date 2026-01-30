package io.simplereactive.scheduler;

import java.util.concurrent.Future;

/**
 * {@link Future}를 {@link Disposable}로 래핑하는 클래스.
 *
 * <p>ExecutorService에 제출된 작업의 취소와 상태 확인을 
 * Disposable 인터페이스로 추상화합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Future<?> future = executor.submit(task);
 * Disposable disposable = new FutureDisposable(future);
 *
 * // 작업 취소
 * disposable.dispose();
 *
 * // 상태 확인
 * if (disposable.isDisposed()) {
 *     System.out.println("작업 완료 또는 취소됨");
 * }
 * }</pre>
 */
public final class FutureDisposable implements Disposable {

    private final Future<?> future;

    /**
     * FutureDisposable을 생성합니다.
     *
     * @param future 래핑할 Future
     */
    public FutureDisposable(Future<?> future) {
        this.future = future;
    }

    /**
     * Future를 취소합니다.
     *
     * <p>실행 중인 작업은 인터럽트하지 않습니다 (mayInterruptIfRunning = false).
     */
    @Override
    public void dispose() {
        future.cancel(false);
    }

    /**
     * Future가 완료되었거나 취소되었는지 확인합니다.
     *
     * @return 완료 또는 취소되었으면 true
     */
    @Override
    public boolean isDisposed() {
        return future.isDone() || future.isCancelled();
    }
}
