package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.subscription.Subscriptions;

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
 * HotPublisher<String> hot = new HotPublisher<>();
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
 * <h2>Backpressure 미지원</h2>
 * <p><strong>주의:</strong> 이 구현은 Backpressure를 지원하지 않습니다 (Rule 1.1 예외).
 * Hot Publisher는 외부 이벤트 소스의 발행 속도를 제어할 수 없기 때문입니다.
 * request(n) 호출은 무시되며, 모든 데이터는 즉시 발행됩니다.
 *
 * <p>실제 운영 환경에서는 다음 전략을 고려하세요:
 * <ul>
 *   <li>onBackpressureBuffer() - 버퍼링</li>
 *   <li>onBackpressureDrop() - 초과 데이터 삭제</li>
 *   <li>onBackpressureLatest() - 최신 값만 유지</li>
 *   <li>sample() - 주기적 샘플링</li>
 * </ul>
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다. 여러 스레드에서 동시에
 * emit(), subscribe(), complete()를 호출해도 안전합니다.
 *
 * @param <T> 발행할 요소의 타입
 * @see io.simplereactive.publisher.ArrayPublisher Cold Publisher 예시
 */
public class HotPublisher<T> implements Publisher<T> {

    private final List<HotSubscription<T>> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    
    // error를 먼저 설정하고 completed를 나중에 설정 (메모리 가시성)
    private volatile Throwable error;

    /**
     * {@inheritDoc}
     *
     * <p>이미 완료된 경우, 즉시 onComplete 또는 onError를 전달합니다.
     * subscribe()와 complete() 호출 사이의 race condition은 
     * CopyOnWriteArrayList와 volatile 플래그로 안전하게 처리됩니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크
        Objects.requireNonNull(subscriber, "Rule 1.9: Subscriber must not be null");

        HotSubscription<T> subscription = new HotSubscription<>(subscriber, this);
        
        // 먼저 리스트에 추가 (complete가 호출되면 이 구독자도 완료 시그널을 받음)
        subscriptions.add(subscription);
        
        // completed 체크 - 이미 완료되었으면 즉시 종료 시그널
        if (completed.get()) {
            subscriptions.remove(subscription);
            subscriber.onSubscribe(Subscriptions.empty());
            Throwable ex = error;
            if (ex != null) {
                subscriber.onError(ex);
            } else {
                subscriber.onComplete();
            }
            return;
        }
        
        // Rule 1.1: onSubscribe 호출
        subscriber.onSubscribe(subscription);
    }

    /**
     * 모든 구독자에게 데이터를 발행합니다.
     *
     * <p>이미 완료된 경우 무시됩니다.
     * 취소된 구독자에게는 발행하지 않습니다.
     * 
     * <p><strong>Backpressure:</strong> request(n)과 관계없이 모든 데이터가 
     * 즉시 발행됩니다. Hot Publisher는 발행 속도를 제어할 수 없습니다.
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
                try {
                    subscription.subscriber.onNext(item);
                } catch (Throwable t) {
                    // Rule 2.13: Subscriber가 예외를 던지면 해당 구독 취소
                    subscription.cancel();
                }
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
                    try {
                        subscription.subscriber.onComplete();
                    } catch (Throwable ignored) {
                        // Rule 2.13: onComplete에서 예외 발생 시 무시
                    }
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
     * @throws NullPointerException Rule 1.9 - t가 null인 경우
     */
    public void error(Throwable t) {
        Objects.requireNonNull(t, "Rule 1.9: Error must not be null");
        
        if (completed.compareAndSet(false, true)) {
            error = t;
            for (HotSubscription<T> subscription : subscriptions) {
                if (!subscription.isCancelled()) {
                    try {
                        subscription.subscriber.onError(t);
                    } catch (Throwable ignored) {
                        // Rule 2.13: onError에서 예외 발생 시 무시
                    }
                }
            }
            subscriptions.clear();
        }
    }

    /**
     * 현재 활성 구독자 수를 반환합니다.
     *
     * <p>취소된 구독자는 제외됩니다.
     *
     * @return 활성 구독자 수
     */
    public int getSubscriberCount() {
        return (int) subscriptions.stream()
                .filter(s -> !s.isCancelled())
                .count();
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
     * request()는 아무 동작도 하지 않습니다 (Rule 1.1 예외).
     */
    private static final class HotSubscription<T> implements Subscription {

        final Subscriber<? super T> subscriber;
        private final HotPublisher<T> parent;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        HotSubscription(Subscriber<? super T> subscriber, HotPublisher<T> parent) {
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
}
