package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 여러 Subscriber에게 같은 데이터를 발행하는 Hot Publisher.
 *
 * <p>Cold Publisher와 달리, 구독 시점과 관계없이 데이터를 발행하며
 * 늦게 구독한 Subscriber는 이전 데이터를 받지 못합니다.
 *
 * <h2>Cold vs Hot 비교</h2>
 * <pre>
 * Cold Publisher (예: ArrayPublisher)
 * ─────────────────────────────────────────
 * - 구독할 때마다 처음부터 발행
 * - 비유: VOD (주문형 비디오)
 *
 * Hot Publisher (이 클래스)
 * ─────────────────────────────────────────
 * - 구독 시점과 관계없이 계속 발행
 * - 늦게 구독하면 이전 데이터 놓침
 * - 비유: 실시간 TV 방송
 * </pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * 시간 ──────────────────────────────────────>
 * emit()     1     2     3     4     5
 *            ↓     ↓     ↓     ↓     ↓
 * Subscriber A ────[1]──[2]──[3]──[4]──[5]──
 *                        ↑
 * Subscriber B ──────────[2]──[3]──[4]──[5]──  ← 2부터!
 *                              ↑
 * Subscriber C ────────────────[3]──[4]──[5]──  ← 3부터!
 * </pre>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * SimpleHotPublisher<String> hot = new SimpleHotPublisher<>();
 *
 * // 첫 번째 구독자
 * hot.subscribe(subscriberA);
 *
 * hot.emit("Hello");  // A만 받음
 *
 * // 두 번째 구독자 (늦게 구독)
 * hot.subscribe(subscriberB);
 *
 * hot.emit("World");  // A, B 모두 받음
 *
 * hot.complete();
 * }</pre>
 *
 * <h2>실제 사용 사례</h2>
 * <ul>
 *   <li>주식 시세 - 모든 구독자가 같은 시세를 받음</li>
 *   <li>센서 데이터 - 실시간 측정값</li>
 *   <li>사용자 이벤트 - 클릭, 키보드 입력 등</li>
 *   <li>웹소켓 메시지 - 서버에서 푸시되는 데이터</li>
 * </ul>
 *
 * <h2>Backpressure</h2>
 * <p>이 구현은 Backpressure를 지원하지 않습니다.
 * Hot Publisher는 일반적으로 발행 속도를 제어할 수 없기 때문입니다.
 * 실제 운영 환경에서는 버퍼링이나 샘플링 전략이 필요합니다.
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다. 여러 스레드에서 동시에
 * emit(), subscribe(), complete()를 호출해도 안전합니다.
 *
 * @param <T> 발행할 요소의 타입
 * @see io.simplereactive.publisher.ArrayPublisher Cold Publisher 예시
 */
public class SimpleHotPublisher<T> implements Publisher<T> {

    private final List<HotSubscription<T>> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    
    // error를 먼저 설정하고 completed를 나중에 설정 (메모리 가시성)
    private volatile Throwable error;

    /**
     * {@inheritDoc}
     *
     * <p>이미 완료된 경우, 즉시 onComplete 또는 onError를 전달합니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크
        Objects.requireNonNull(subscriber, "Rule 1.9: Subscriber must not be null");

        if (completed.get()) {
            // 이미 완료된 경우 - 즉시 종료 시그널 전달
            subscriber.onSubscribe(new EmptySubscription());
            Throwable ex = error;
            if (ex != null) {
                subscriber.onError(ex);
            } else {
                subscriber.onComplete();
            }
            return;
        }

        HotSubscription<T> subscription = new HotSubscription<>(subscriber, this);
        subscriptions.add(subscription);
        
        // Rule 1.1: onSubscribe 호출
        subscriber.onSubscribe(subscription);
    }

    /**
     * 모든 구독자에게 데이터를 발행합니다.
     *
     * <p>이미 완료된 경우 무시됩니다.
     * 취소된 구독자에게는 발행하지 않습니다.
     *
     * @param item 발행할 데이터
     * @throws NullPointerException Rule 2.13 - item이 null인 경우
     */
    public void emit(T item) {
        // Rule 2.13: null 체크
        Objects.requireNonNull(item, "Rule 2.13: emit item must not be null");
        
        if (completed.get()) {
            return;
        }

        for (HotSubscription<T> subscription : subscriptions) {
            if (!subscription.isCancelled()) {
                subscription.subscriber.onNext(item);
            }
        }
    }

    /**
     * 모든 구독자에게 완료 시그널을 보냅니다.
     *
     * <p>한 번만 호출할 수 있습니다. 이후 emit() 호출은 무시됩니다.
     */
    public void complete() {
        if (completed.compareAndSet(false, true)) {
            for (HotSubscription<T> subscription : subscriptions) {
                if (!subscription.isCancelled()) {
                    subscription.subscriber.onComplete();
                }
            }
            subscriptions.clear();
        }
    }

    /**
     * 모든 구독자에게 에러 시그널을 보냅니다.
     *
     * <p>한 번만 호출할 수 있습니다. 이후 emit() 호출은 무시됩니다.
     *
     * @param t 발생한 에러
     * @throws NullPointerException t가 null인 경우
     */
    public void error(Throwable t) {
        Objects.requireNonNull(t, "Error must not be null");
        
        if (completed.compareAndSet(false, true)) {
            error = t;
            for (HotSubscription<T> subscription : subscriptions) {
                if (!subscription.isCancelled()) {
                    subscription.subscriber.onError(t);
                }
            }
            subscriptions.clear();
        }
    }

    /**
     * 현재 활성 구독자 수를 반환합니다.
     *
     * @return 구독자 수
     */
    public int getSubscriberCount() {
        return subscriptions.size();
    }

    /**
     * 완료 여부를 반환합니다.
     *
     * @return 완료되었으면 true
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * 구독을 제거합니다.
     */
    void removeSubscription(HotSubscription<T> subscription) {
        subscriptions.remove(subscription);
    }

    /**
     * Hot Publisher용 Subscription.
     *
     * <p>Hot Publisher는 Backpressure를 지원하지 않으므로
     * request()는 아무 동작도 하지 않습니다.
     */
    private static final class HotSubscription<T> implements Subscription {

        private final Subscriber<? super T> subscriber;
        private final SimpleHotPublisher<T> parent;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        HotSubscription(Subscriber<? super T> subscriber, SimpleHotPublisher<T> parent) {
            this.subscriber = subscriber;
            this.parent = parent;
        }

        /**
         * Hot Publisher는 Backpressure를 지원하지 않습니다.
         *
         * <p>발행 속도는 외부에서 emit()을 호출하는 속도에 의해 결정됩니다.
         * 실제 운영 환경에서는 버퍼링이나 샘플링 전략이 필요합니다.
         *
         * @param n 요청량 (무시됨)
         */
        @Override
        public void request(long n) {
            // Hot Publisher는 demand를 무시
            // 발행 속도는 emit() 호출 속도에 의해 결정됨
        }

        /**
         * 구독을 취소합니다.
         *
         * <p>취소된 후에는 더 이상 데이터를 받지 않습니다.
         */
        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                parent.removeSubscription(this);
            }
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    /**
     * 완료된 Publisher에 구독할 때 사용되는 빈 Subscription.
     */
    private static final class EmptySubscription implements Subscription {
        @Override
        public void request(long n) {
            // 이미 완료되었으므로 무시
        }

        @Override
        public void cancel() {
            // 이미 완료되었으므로 무시
        }
    }
}
