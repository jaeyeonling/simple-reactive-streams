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
 */
public final class MapOperator<T, R> extends AbstractOperator<T, R> {

    private final Function<? super T, ? extends R> mapper;

    /**
     * MapOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param mapper 변환 함수
     * @throws NullPointerException upstream 또는 mapper가 null인 경우
     */
    public MapOperator(Publisher<T> upstream, Function<? super T, ? extends R> mapper) {
        super(upstream);
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    @Override
    protected Subscriber<T> createSubscriber(Subscriber<? super R> downstream) {
        return new MapSubscriber<>(downstream, mapper);
    }

    /**
     * Map 변환을 수행하는 Subscriber.
     *
     * <p>upstream에서 받은 요소에 mapper 함수를 적용하여
     * 변환된 값을 downstream으로 전달합니다.
     *
     * @param <T> 입력 타입
     * @param <R> 출력 타입
     */
    private static final class MapSubscriber<T, R> extends AbstractOperatorSubscriber<T, R> {

        private final Function<? super T, ? extends R> mapper;

        MapSubscriber(Subscriber<? super R> downstream, Function<? super T, ? extends R> mapper) {
            super(downstream);
            this.mapper = mapper;
        }

        @Override
        public void onNext(T item) {
            if (isDone()) {
                return;
            }

            R mapped;
            try {
                mapped = mapper.apply(item);
            } catch (Throwable t) {
                // mapper에서 예외 발생 시 에러 처리
                cancelUpstream();
                onError(t);
                return;
            }

            // Rule 2.13: null 체크
            if (mapped == null) {
                cancelUpstream();
                onError(new NullPointerException("Mapper returned null for item: " + item));
                return;
            }

            downstream.onNext(mapped);
        }

        @Override
        public void onError(Throwable t) {
            if (markDone()) {
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (markDone()) {
                downstream.onComplete();
            }
        }
    }
}
