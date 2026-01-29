package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.subscription.ArraySubscription;

/**
 * 배열의 요소를 순서대로 발행하는 Publisher.
 *
 * <p>Cold Publisher로, 구독할 때마다 처음부터 발행합니다.
 * 각 Subscriber는 독립적인 {@link ArraySubscription}을 받습니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Publisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
 * publisher.subscribe(subscriber);
 * }</pre>
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 1.1: Publisher.subscribe는 onSubscribe를 호출해야 한다</li>
 *   <li>Rule 1.9: subscribe의 인자가 null이면 NullPointerException을 던져야 한다</li>
 * </ul>
 *
 * @param <T> 발행할 요소의 타입
 * @see ArraySubscription
 */
public class ArrayPublisher<T> implements Publisher<T> {

    private final T[] array;

    /**
     * 주어진 배열을 발행하는 Publisher를 생성합니다.
     *
     * @param array 발행할 요소들
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public ArrayPublisher(T... array) {
        this.array = array;
    }

    /**
     * {@inheritDoc}
     *
     * <p>새로운 {@link ArraySubscription}을 생성하여 Subscriber에게 전달합니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
        }

        // Rule 1.1: onSubscribe 호출
        ArraySubscription<T> subscription = new ArraySubscription<>(subscriber, array);
        subscriber.onSubscribe(subscription);
    }
}
