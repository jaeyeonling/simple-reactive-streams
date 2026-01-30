package io.simplereactive.scheduler;

/**
 * 리소스 해제를 위한 인터페이스.
 *
 * <p>스케줄된 작업의 취소나 구독 해제 등에 사용됩니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Disposable disposable = scheduler.schedule(() -> {
 *     // 작업 내용
 * });
 *
 * // 필요 시 작업 취소
 * disposable.dispose();
 *
 * // 이미 취소되었는지 확인
 * if (disposable.isDisposed()) {
 *     System.out.println("이미 취소됨");
 * }
 * }</pre>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>Reactive Streams의 Subscription.cancel()과 유사한 역할</li>
 *   <li>dispose()는 멱등성을 가져야 함 (여러 번 호출해도 안전)</li>
 *   <li>리소스 누수를 방지하기 위해 반드시 dispose 필요</li>
 * </ul>
 */
public interface Disposable {

    /**
     * 리소스를 해제하거나 작업을 취소합니다.
     *
     * <p>이 메서드는 멱등성을 가져야 합니다.
     * 즉, 여러 번 호출해도 한 번 호출한 것과 동일한 효과를 가져야 합니다.
     */
    void dispose();

    /**
     * 리소스가 이미 해제되었는지 확인합니다.
     *
     * @return 해제되었으면 true
     */
    boolean isDisposed();

    /**
     * 이미 dispose된 상태를 나타내는 싱글톤 Disposable.
     *
     * <p>아무 작업도 하지 않는 빈 Disposable이 필요할 때 사용합니다.
     */
    Disposable DISPOSED = new Disposable() {
        @Override
        public void dispose() {
            // 이미 dispose된 상태이므로 아무것도 하지 않음
        }

        @Override
        public boolean isDisposed() {
            return true;
        }

        @Override
        public String toString() {
            return "Disposable.DISPOSED";
        }
    };
}
