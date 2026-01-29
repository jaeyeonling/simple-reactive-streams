package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

import java.util.Objects;
import java.util.function.Function;

/**
 * 각 요소를 변환하는 Operator.
 *
 * <p>upstream에서 받은 각 요소에 mapper 함수를 적용하여
 * 변환된 값을 downstream으로 전달합니다.
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * upstream:   ──1──2──3──4──5──|
 *                    │
 *              map(x -> x * 2)
 *                    │
 * downstream: ──2──4──6──8──10─|
 * </pre>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │              MapOperator                    │
 * ├─────────────────────────────────────────────┤
 * │                                             │
 * │  upstream ──onNext(T)──> [mapper.apply(T)] │
 * │                               │            │
 * │                          onNext(R)         │
 * │                               │            │
 * │                               ▼            │
 * │                          downstream        │
 * │                                             │
 * └─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Backpressure</h2>
 * <p>1:1 변환이므로 downstream의 request를 그대로 upstream에 전달합니다.
 *
 * @param <T> 입력 타입
 * @param <R> 출력 타입
 * @see MapSubscriber
 */
public final class MapOperator<T, R> implements Publisher<R> {

    private final Publisher<T> upstream;
    private final Function<? super T, ? extends R> mapper;

    /**
     * MapOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param mapper 변환 함수
     * @throws NullPointerException upstream 또는 mapper가 null인 경우
     */
    public MapOperator(Publisher<T> upstream, Function<? super T, ? extends R> mapper) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        upstream.subscribe(new MapSubscriber<>(subscriber, mapper));
    }
}
