package io.simplereactive.core;

/**
 * 데이터 스트림의 발행자.
 * 
 * <p>Publisher는 무한하거나 유한한 순차적 요소들을 발행할 수 있습니다.
 * Subscriber가 {@link #subscribe(Subscriber)}를 호출하면 데이터 발행을 시작합니다.</p>
 * 
 * <h2>규약</h2>
 * <ul>
 *   <li>Rule 1.1: subscribe가 호출되면 반드시 onSubscribe를 호출해야 한다</li>
 *   <li>Rule 1.9: subscribe의 인자가 null이면 NullPointerException을 던져야 한다</li>
 * </ul>
 * 
 * @param <T> 발행할 요소의 타입
 * @see Subscriber
 * @see Subscription
 */
public interface Publisher<T> {
    
    /**
     * Subscriber의 구독을 받아들입니다.
     * 
     * <p>이 메서드가 호출되면 Publisher는 반드시 {@link Subscriber#onSubscribe(Subscription)}를
     * 호출해야 합니다 (Rule 1.1).</p>
     * 
     * @param subscriber 데이터를 받을 Subscriber (null 불가)
     * @throws NullPointerException subscriber가 null인 경우 (Rule 1.9)
     */
    void subscribe(Subscriber<? super T> subscriber);
}
