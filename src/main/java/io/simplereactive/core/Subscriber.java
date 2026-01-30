package io.simplereactive.core;

/**
 * 데이터 스트림의 구독자.
 * 
 * <p>Subscriber는 Publisher로부터 데이터를 수신하고 처리합니다.
 * 시그널은 다음 순서로 호출됩니다:</p>
 * 
 * <pre>
 * onSubscribe → onNext* → (onError | onComplete)?
 * </pre>
 * 
 * <h2>규약</h2>
 * <ul>
 *   <li>Rule 2.1: Subscriber는 onSubscribe를 통해 Subscription을 받은 후에야 request를 호출할 수 있다</li>
 *   <li>Rule 2.5: Subscriber는 onSubscribe 이후에 request를 동기적으로 호출할 수 있다</li>
 * </ul>
 * 
 * @param <T> 수신할 요소의 타입
 * @see Publisher
 * @see Subscription
 */
public interface Subscriber<T> {
    
    /**
     * 구독이 시작될 때 호출됩니다.
     * 
     * <p>이 메서드에서 {@link Subscription#request(long)}를 호출하여
     * 데이터를 요청하거나 {@link Subscription#cancel()}을 호출하여
     * 구독을 취소할 수 있습니다.</p>
     * 
     * @param subscription Publisher와의 구독 관계 (null 불가)
     */
    void onSubscribe(Subscription subscription);
    
    /**
     * 데이터 요소가 도착할 때마다 호출됩니다.
     * 
     * <p>{@link Subscription#request(long)}으로 요청한 개수만큼만 호출됩니다.</p>
     * 
     * @param item 수신한 데이터 요소 (null 불가)
     */
    void onNext(T item);
    
    /**
     * 에러 발생 시 호출됩니다.
     * 
     * <p>터미널 시그널입니다. 이후에는 어떤 시그널도 호출되지 않습니다.</p>
     * 
     * @param throwable 발생한 에러 (null 불가)
     */
    void onError(Throwable throwable);
    
    /**
     * 모든 데이터 전송이 완료되었을 때 호출됩니다.
     * 
     * <p>터미널 시그널입니다. 이후에는 어떤 시그널도 호출되지 않습니다.</p>
     */
    void onComplete();
}
