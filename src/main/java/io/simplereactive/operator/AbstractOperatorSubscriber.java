package io.simplereactive.operator;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Operator Subscriber의 공통 기능을 제공하는 추상 클래스.
 *
 * <p>모든 Operator의 내부 Subscriber는 이 클래스를 상속하여
 * 일관된 동작과 스레드 안전성을 보장합니다.
 *
 * <h2>제공하는 기능</h2>
 * <ul>
 *   <li>스레드 안전한 {@code done} 상태 관리 (AtomicBoolean)</li>
 *   <li>스레드 안전한 {@code upstream} 관리 (AtomicReference)</li>
 *   <li>중복 onSubscribe 호출 방지 (Rule 2.12)</li>
 *   <li>cancel 시 done 플래그 설정</li>
 *   <li>request 위임</li>
 * </ul>
 *
 * <h2>사용 방법</h2>
 * <pre>{@code
 * private static final class MySubscriber<T, R> extends AbstractOperatorSubscriber<T, R> {
 *     
 *     MySubscriber(Subscriber<? super R> downstream) {
 *         super(downstream);
 *     }
 *
 *     @Override
 *     public void onNext(T item) {
 *         if (isDone()) return;
 *         // 변환 로직
 *         downstream.onNext(transformed);
 *     }
 *
 *     @Override
 *     public void onError(Throwable t) {
 *         if (markDone()) {
 *             downstream.onError(t);
 *         }
 *     }
 *
 *     @Override
 *     public void onComplete() {
 *         if (markDone()) {
 *             downstream.onComplete();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>관련 규약</h2>
 * <ul>
 *   <li>Rule 2.3: Subscriber.onSubscribe는 최대 한 번만 호출</li>
 *   <li>Rule 2.4: 구독 중 Subscription.cancel 호출 허용</li>
 *   <li>Rule 2.12: onSubscribe가 여러 번 호출되면 예외</li>
 * </ul>
 *
 * @param <T> 입력 타입 (upstream에서 받는 타입)
 * @param <R> 출력 타입 (downstream으로 전달하는 타입)
 */
public abstract class AbstractOperatorSubscriber<T, R> implements Subscriber<T>, Subscription {

    /**
     * 실제 데이터를 받을 downstream Subscriber.
     */
    protected final Subscriber<? super R> downstream;

    /**
     * 완료 상태를 나타내는 플래그.
     * <p>true면 더 이상 시그널을 처리하지 않습니다.
     */
    private final AtomicBoolean done = new AtomicBoolean(false);

    /**
     * upstream Subscription 참조.
     * <p>AtomicReference를 사용하여 스레드 안전한 접근과
     * 중복 onSubscribe 방지를 보장합니다.
     */
    private final AtomicReference<Subscription> upstream = new AtomicReference<>();

    /**
     * AbstractOperatorSubscriber를 생성합니다.
     *
     * @param downstream 실제 데이터를 받을 Subscriber
     * @throws NullPointerException downstream이 null인 경우
     */
    protected AbstractOperatorSubscriber(Subscriber<? super R> downstream) {
        this.downstream = Objects.requireNonNull(downstream, "Downstream must not be null");
    }

    // ========== Subscriber 구현 ==========

    /**
     * upstream에서 Subscription을 받으면 downstream에 전달합니다.
     *
     * <p>중복 호출 시 두 번째 Subscription은 즉시 cancel됩니다. (Rule 2.12)
     *
     * @param s upstream Subscription
     */
    @Override
    public void onSubscribe(Subscription s) {
        // Rule 2.12: 중복 onSubscribe 방지
        if (upstream.compareAndSet(null, s)) {
            // 자신(this)을 Subscription으로 전달하여 request/cancel 중개
            downstream.onSubscribe(this);
        } else {
            // 이미 구독된 상태면 새 Subscription은 cancel
            s.cancel();
        }
    }

    // ========== Subscription 구현 ==========

    /**
     * downstream의 request를 upstream으로 전달합니다.
     *
     * <p>Rule 3.9에 따라 n <= 0인 경우 onError를 시그널합니다.
     *
     * @param n 요청할 데이터 개수
     */
    @Override
    public void request(long n) {
        // Rule 3.9: n <= 0이면 에러 시그널
        if (n <= 0) {
            cancel();
            if (markDone()) {
                downstream.onError(new IllegalArgumentException(
                        "Rule 3.9: request amount must be positive, but was " + n));
            }
            return;
        }
        
        Subscription s = upstream.get();
        if (s != null) {
            s.request(n);
        }
    }

    /**
     * 구독을 취소합니다.
     *
     * <p>done 플래그를 설정하고 upstream을 cancel합니다.
     */
    @Override
    public void cancel() {
        if (done.compareAndSet(false, true)) {
            Subscription s = upstream.get();
            if (s != null) {
                s.cancel();
            }
        }
    }

    // ========== 유틸리티 메서드 ==========

    /**
     * 완료 상태인지 확인합니다.
     *
     * @return 완료 상태이면 true
     */
    protected final boolean isDone() {
        return done.get();
    }

    /**
     * 완료 상태로 전환합니다.
     *
     * <p>이미 완료 상태면 false를 반환합니다.
     * compareAndSet을 사용하여 한 번만 성공합니다.
     *
     * @return 상태 전환에 성공하면 true, 이미 완료 상태면 false
     */
    protected final boolean markDone() {
        return done.compareAndSet(false, true);
    }

    /**
     * upstream Subscription을 직접 가져옵니다.
     *
     * <p>특별한 경우(예: cancel 후 재요청 방지)에만 사용하세요.
     *
     * @return upstream Subscription (null일 수 있음)
     */
    protected final Subscription getUpstream() {
        return upstream.get();
    }

    /**
     * upstream을 cancel합니다.
     *
     * <p>done 플래그는 변경하지 않습니다.
     * onNext에서 에러 발생 시 upstream만 cancel하고
     * onError로 전환할 때 사용합니다.
     */
    protected final void cancelUpstream() {
        Subscription s = upstream.get();
        if (s != null) {
            s.cancel();
        }
    }
}
