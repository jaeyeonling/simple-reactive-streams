package io.simplereactive.core;

/**
 * Publisher와 Subscriber 간의 구독 관계.
 * 
 * <p>Subscription은 Subscriber가 데이터를 요청하거나 구독을 취소하는 데 사용됩니다.
 * 단일 Subscriber에 대해 하나의 Subscription만 존재해야 합니다.</p>
 * 
 * <h2>규약</h2>
 * <ul>
 *   <li>Rule 3.3: request는 Publisher에게 지정된 수의 onNext 호출을 요청한다</li>
 *   <li>Rule 3.9: request(n)에서 n <= 0이면 onError(IllegalArgumentException)를 시그널해야 한다</li>
 *   <li>Rule 3.13: cancel은 멱등해야 한다</li>
 * </ul>
 * 
 * @see Publisher
 * @see Subscriber
 */
public interface Subscription {
    
    /**
     * Publisher에게 n개의 데이터를 요청합니다.
     * 
     * <p>이 메서드는 여러 번 호출될 수 있으며, 요청은 누적됩니다.
     * n <= 0이면 {@link Subscriber#onError(Throwable)}가 호출됩니다 (Rule 3.9).</p>
     * 
     * @param n 요청할 데이터 개수 (양수여야 함)
     */
    void request(long n);
    
    /**
     * 구독을 취소합니다.
     * 
     * <p>이 메서드 호출 후에는 더 이상 데이터를 받지 않습니다.
     * 여러 번 호출해도 안전합니다 (멱등, Rule 3.13).</p>
     */
    void cancel();
}
