package io.simplereactive.subscriber;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.subscription.BufferedSubscription;

import java.util.Objects;

/**
 * 버퍼를 사용하여 Backpressure를 처리하는 Subscriber.
 *
 * <p>upstream(Publisher)으로부터 받은 데이터를 버퍼에 저장하고,
 * downstream(실제 Subscriber)의 요청에 따라 전달합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * Publisher ──onNext──> BufferedSubscriber ──onNext──> Downstream
 *                              │
 *                    [BufferedSubscription]
 *                              │
 *                   request(bufferSize) prefetch
 * </pre>
 *
 * <h2>오버플로우 처리</h2>
 * <p>버퍼가 가득 찬 상태에서 새 데이터가 도착하면
 * {@link OverflowStrategy}에 따라 처리합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * var downstream = new MySubscriber<>();
 * var buffered = new BufferedSubscriber<>(downstream, 100, OverflowStrategy.DROP_OLDEST);
 * publisher.subscribe(buffered);
 * }</pre>
 *
 * @param <T> 요소 타입
 * @see OverflowStrategy
 * @see BufferedSubscription
 */
public class BufferedSubscriber<T> implements Subscriber<T> {

    private final Subscriber<? super T> downstream;
    private final int bufferSize;
    private final OverflowStrategy strategy;

    private volatile BufferedSubscription<T> subscription;

    /**
     * BufferedSubscriber를 생성합니다.
     *
     * @param downstream 데이터를 전달받을 하위 Subscriber
     * @param bufferSize 버퍼 크기 (양수)
     * @param strategy 오버플로우 전략
     * @throws NullPointerException downstream 또는 strategy가 null인 경우
     * @throws IllegalArgumentException bufferSize가 0 이하인 경우
     */
    public BufferedSubscriber(
            Subscriber<? super T> downstream,
            int bufferSize,
            OverflowStrategy strategy) {
        this.downstream = Objects.requireNonNull(downstream, "Downstream must not be null");
        this.strategy = Objects.requireNonNull(strategy, "Strategy must not be null");

        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive, but was " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    /**
     * 기본 전략(DROP_LATEST)으로 BufferedSubscriber를 생성합니다.
     *
     * @param downstream 데이터를 전달받을 하위 Subscriber
     * @param bufferSize 버퍼 크기
     */
    public BufferedSubscriber(Subscriber<? super T> downstream, int bufferSize) {
        this(downstream, bufferSize, OverflowStrategy.DROP_LATEST);
    }

    @Override
    public void onSubscribe(Subscription s) {
        // Rule 2.5: 중복 구독 방지
        if (this.subscription != null) {
            s.cancel();
            subscription.onError(new IllegalStateException(
                    "Rule 2.5: onSubscribe must not be called more than once"));
            return;
        }

        this.subscription = new BufferedSubscription<>(downstream, s, bufferSize, strategy);
        downstream.onSubscribe(subscription);

        // prefetch: 버퍼 크기만큼 미리 요청
        s.request(bufferSize);
    }

    @Override
    public void onNext(T item) {
        BufferedSubscription<T> s = this.subscription;
        if (s != null) {
            s.onNext(item);
        }
    }

    @Override
    public void onError(Throwable t) {
        BufferedSubscription<T> s = this.subscription;
        if (s != null) {
            s.onError(t);
        }
    }

    @Override
    public void onComplete() {
        BufferedSubscription<T> s = this.subscription;
        if (s != null) {
            s.onComplete();
        }
    }
}
