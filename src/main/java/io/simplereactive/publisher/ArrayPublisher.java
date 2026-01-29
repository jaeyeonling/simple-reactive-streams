package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 배열의 요소를 순서대로 발행하는 Publisher.
 *
 * <p>Cold Publisher로, 구독할 때마다 처음부터 발행합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
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

    /**
     * ArrayPublisher의 Subscription 구현.
     *
     * <p>각 Subscriber마다 독립적인 Subscription이 생성됩니다.
     * 이를 통해 Cold Publisher 특성을 유지합니다.
     *
     * <h2>상태 관리</h2>
     * <ul>
     *   <li>index: 현재 발행 위치</li>
     *   <li>requested: 남은 demand (요청량)</li>
     *   <li>cancelled: 취소 여부</li>
     * </ul>
     *
     * @param <T> 요소 타입
     */
    static class ArraySubscription<T> implements Subscription {

        private final Subscriber<? super T> subscriber;
        private final T[] array;

        private int index = 0;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ArraySubscription(Subscriber<? super T> subscriber, T[] array) {
            this.subscriber = subscriber;
            this.array = array;
        }

        /**
         * {@inheritDoc}
         *
         * <p>n개의 데이터를 요청합니다. 요청량은 누적되며,
         * 요청된 만큼만 onNext가 호출됩니다.
         *
         * @throws IllegalArgumentException Rule 3.9 - n이 0 이하인 경우 (onError로 전달)
         */
        @Override
        public void request(long n) {
            // Rule 3.9: n <= 0이면 에러
            if (n <= 0) {
                subscriber.onError(
                        new IllegalArgumentException("Rule 3.9: n must be positive, but was " + n)
                );
                return;
            }

            // demand 추가 (overflow 방지)
            long current, next;
            do {
                current = requested.get();
                if (current == Long.MAX_VALUE) {
                    return; // 이미 unbounded
                }
                next = current + n;
                if (next < 0) {
                    next = Long.MAX_VALUE; // overflow → unbounded
                }
            } while (!requested.compareAndSet(current, next));

            // 데이터 발행
            drain();
        }

        /**
         * {@inheritDoc}
         *
         * <p>구독을 취소합니다. 취소 후에는 더 이상 시그널이 발생하지 않습니다.
         */
        @Override
        public void cancel() {
            // Rule 3.5: cancel은 멱등성을 가져야 함
            cancelled.set(true);
        }

        /**
         * 요청된 만큼 데이터를 발행합니다.
         *
         * <p>이 메서드는 다음 조건에서 중단됩니다:
         * <ul>
         *   <li>요청량(demanded)이 0이 된 경우</li>
         *   <li>배열의 모든 요소를 발행한 경우</li>
         *   <li>취소된 경우</li>
         *   <li>에러가 발생한 경우</li>
         * </ul>
         */
        private void drain() {
            // 이미 취소되었으면 중단
            if (cancelled.get()) {
                return;
            }

            // 요청된 만큼 발행
            while (requested.get() > 0 && index < array.length) {
                // 취소 체크
                if (cancelled.get()) {
                    return;
                }

                T item = array[index++];

                // Rule 2.13: null 체크
                if (item == null) {
                    subscriber.onError(
                            new NullPointerException("Rule 2.13: onNext must not be called with null")
                    );
                    return;
                }

                subscriber.onNext(item);

                // demand 감소
                if (requested.get() != Long.MAX_VALUE) {
                    requested.decrementAndGet();
                }
            }

            // 모든 요소 발행 완료
            if (index >= array.length && !cancelled.get()) {
                subscriber.onComplete();
            }
        }
    }
}
