package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

/**
 * 빈 Publisher 구현.
 *
 * <p>구독 시 즉시 onComplete를 호출합니다.
 * 데이터를 발행하지 않고 완료만 알립니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * ──|  (즉시 완료)
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Publisher<String> empty = EmptyPublisher.instance();
 * empty.subscribe(subscriber);
 * // subscriber.onSubscribe() 호출
 * // subscriber.request(n) 시 즉시 onComplete() 호출
 * }</pre>
 *
 * @param <T> 요소 타입 (실제로는 발행되지 않음)
 * @see EmptySubscription
 */
public final class EmptyPublisher<T> implements Publisher<T> {

    @SuppressWarnings("rawtypes")
    private static final EmptyPublisher INSTANCE = new EmptyPublisher<>();

    private EmptyPublisher() {
    }

    /**
     * EmptyPublisher 싱글톤 인스턴스를 반환합니다.
     *
     * @param <T> 요소 타입
     * @return EmptyPublisher 인스턴스
     */
    @SuppressWarnings("unchecked")
    public static <T> EmptyPublisher<T> instance() {
        return (EmptyPublisher<T>) INSTANCE;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new EmptySubscription(subscriber));
    }
}
