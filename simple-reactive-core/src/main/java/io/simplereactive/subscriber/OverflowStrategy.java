package io.simplereactive.subscriber;

/**
 * 버퍼 오버플로우 시 처리 전략.
 *
 * <p>BufferedSubscriber에서 버퍼가 가득 찼을 때
 * 새로운 데이터를 어떻게 처리할지 결정합니다.
 *
 * <h2>전략별 특성</h2>
 * <table border="1">
 *   <tr><th>전략</th><th>데이터 손실</th><th>에러</th><th>사용 사례</th></tr>
 *   <tr><td>DROP_OLDEST</td><td>O (오래된 것)</td><td>X</td><td>실시간 모니터링</td></tr>
 *   <tr><td>DROP_LATEST</td><td>O (새로운 것)</td><td>X</td><td>샘플링</td></tr>
 *   <tr><td>ERROR</td><td>X</td><td>O</td><td>데이터 무결성 중요</td></tr>
 * </table>
 *
 * @see BufferedSubscriber
 */
public enum OverflowStrategy {

    /**
     * 가장 오래된 데이터를 버리고 새 데이터를 저장합니다.
     *
     * <p>실시간 모니터링처럼 최신 데이터가 더 중요한 경우 사용합니다.
     *
     * <pre>
     * Before: [A][B][C] (full)
     * New: D
     * After:  [B][C][D] (A dropped)
     * </pre>
     */
    DROP_OLDEST,

    /**
     * 새로운 데이터를 버리고 기존 데이터를 유지합니다.
     *
     * <p>샘플링이나 처리 순서가 중요한 경우 사용합니다.
     *
     * <pre>
     * Before: [A][B][C] (full)
     * New: D
     * After:  [A][B][C] (D dropped)
     * </pre>
     */
    DROP_LATEST,

    /**
     * 오버플로우 시 에러를 발생시킵니다.
     *
     * <p>데이터 손실이 허용되지 않는 경우 사용합니다.
     * onError(BufferOverflowException)가 호출됩니다.
     */
    ERROR
}
