package io.simplereactive.subscription;

import io.simplereactive.core.Subscription;

/**
 * 자주 사용되는 Subscription 인스턴스를 제공하는 유틸리티 클래스.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // 이미 완료된 Publisher에서 사용
 * if (completed.get()) {
 *     subscriber.onSubscribe(Subscriptions.empty());
 *     subscriber.onComplete();
 *     return;
 * }
 * }</pre>
 *
 * @see Subscription
 */
public final class Subscriptions {

    private Subscriptions() {
        // 인스턴스화 방지
    }

    /**
     * 빈 Subscription 싱글톤 인스턴스.
     */
    private static final Subscription EMPTY = new Subscription() {
        @Override
        public void request(long n) {
            // 아무 동작 없음 - 이미 완료됨
        }

        @Override
        public void cancel() {
            // 아무 동작 없음 - 이미 완료됨
        }

        @Override
        public String toString() {
            return "Subscriptions.empty()";
        }
    };

    /**
     * 아무 동작도 하지 않는 빈 Subscription을 반환합니다.
     *
     * <p>이미 완료되었거나 취소된 Publisher에서 사용합니다.
     * request()와 cancel() 호출이 무시됩니다.
     *
     * <h3>사용 사례</h3>
     * <ul>
     *   <li>이미 onComplete/onError가 발행된 Publisher에 늦게 구독할 때</li>
     *   <li>Empty Publisher에서 즉시 완료할 때</li>
     *   <li>Hot Publisher가 이미 종료된 상태일 때</li>
     * </ul>
     *
     * @return 빈 Subscription (싱글톤)
     */
    public static Subscription empty() {
        return EMPTY;
    }
}
