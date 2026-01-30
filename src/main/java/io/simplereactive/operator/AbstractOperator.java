package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

import java.util.Objects;

/**
 * Operator의 공통 기능을 제공하는 추상 클래스.
 *
 * <p>모든 Operator는 이 클래스를 상속하여 일관된 구조를 유지합니다.
 *
 * <h2>사용 방법</h2>
 * <pre>{@code
 * public class MyOperator<T> extends AbstractOperator<T, T> {
 *     public MyOperator(Publisher<T> upstream) {
 *         super(upstream);
 *     }
 *
 *     @Override
 *     protected Subscriber<T> createSubscriber(Subscriber<? super T> downstream) {
 *         return new MySubscriber(downstream);
 *     }
 * }
 * }</pre>
 *
 * @param <T> 입력 타입
 * @param <R> 출력 타입
 */
public abstract class AbstractOperator<T, R> implements Publisher<R> {

    protected final Publisher<T> upstream;

    /**
     * AbstractOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @throws NullPointerException upstream이 null인 경우
     */
    protected AbstractOperator(Publisher<T> upstream) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>새로운 Subscriber를 생성하여 upstream에 구독합니다.
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public final void subscribe(Subscriber<? super R> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(createSubscriber(subscriber));
    }

    /**
     * downstream을 감싸는 Subscriber를 생성합니다.
     *
     * <p>하위 클래스는 이 메서드를 구현하여 Operator 로직을 담은
     * Subscriber를 반환해야 합니다.
     *
     * @param downstream 실제 데이터를 받을 Subscriber
     * @return upstream을 구독할 Subscriber
     */
    protected abstract Subscriber<T> createSubscriber(Subscriber<? super R> downstream);
}
