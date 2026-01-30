package io.simplereactive.subscription;

import io.simplereactive.core.Subscriber;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 배열 요소를 발행하는 Subscription.
 *
 * <p>{@link io.simplereactive.publisher.ArrayPublisher}에서 사용되며,
 * 각 Subscriber마다 독립적인 인스턴스가 생성됩니다 (Cold Publisher 특성).
 *
 * <h2>동작 방식</h2>
 * <ol>
 *   <li>request(n) 호출 시 n개의 요소를 순차적으로 발행</li>
 *   <li>배열 끝에 도달하면 onComplete 호출</li>
 *   <li>cancel 호출 시 발행 중단</li>
 * </ol>
 *
 * <h2>스레드 안전성</h2>
 * <p>index는 AtomicInteger로 관리되어 스레드 안전합니다.
 *
 * @param <T> 요소 타입
 */
public class ArraySubscription<T> extends BaseSubscription<T> {

    private final T[] array;
    private final AtomicInteger index = new AtomicInteger(0);

    /**
     * ArraySubscription을 생성합니다.
     *
     * @param subscriber 데이터를 받을 Subscriber
     * @param array 발행할 배열
     */
    public ArraySubscription(Subscriber<? super T> subscriber, T[] array) {
        super(subscriber);
        this.array = array;
    }

    /**
     * 요청된 만큼 배열 요소를 발행합니다.
     */
    @Override
    protected void doOnRequest() {
        while (hasDemand() && index.get() < array.length) {
            if (isCancelled()) {
                return;
            }

            int i = index.getAndIncrement();
            if (i >= array.length) {
                break; // 다른 스레드가 먼저 증가시킨 경우
            }

            if (!emit(array[i])) {
                return; // emit 실패 (null 또는 에러)
            }
        }

        // 모든 요소 발행 완료
        if (index.get() >= array.length) {
            complete();
        }
    }
}
