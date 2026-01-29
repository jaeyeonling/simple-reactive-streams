package io.simplereactive.operator;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.function.Function;

/**
 * Map 변환을 수행하는 Subscriber.
 *
 * <p>upstream에서 받은 요소에 mapper 함수를 적용하여
 * 변환된 값을 downstream으로 전달합니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * upstream.onNext(T) → mapper.apply(T) → downstream.onNext(R)
 * </pre>
 *
 * <h2>에러 처리</h2>
 * <ul>
 *   <li>mapper에서 예외 발생 시 upstream을 cancel하고 downstream에 onError 전달</li>
 *   <li>mapper가 null 반환 시 NullPointerException으로 onError 전달 (Rule 2.13)</li>
 * </ul>
 *
 * @param <T> 입력 타입
 * @param <R> 출력 타입
 * @see MapOperator
 */
final class MapSubscriber<T, R> implements Subscriber<T> {

    private final Subscriber<? super R> downstream;
    private final Function<? super T, ? extends R> mapper;
    private Subscription upstream;
    private boolean done = false;

    /**
     * MapSubscriber를 생성합니다.
     *
     * @param downstream downstream Subscriber
     * @param mapper 변환 함수
     */
    MapSubscriber(Subscriber<? super R> downstream, Function<? super T, ? extends R> mapper) {
        this.downstream = downstream;
        this.mapper = mapper;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.upstream = s;
        // Subscription을 그대로 전달 (1:1 변환이므로)
        downstream.onSubscribe(s);
    }

    @Override
    public void onNext(T item) {
        if (done) {
            return;
        }

        R mapped;
        try {
            mapped = mapper.apply(item);
        } catch (Throwable t) {
            // mapper에서 예외 발생 시 에러 처리
            upstream.cancel();
            onError(t);
            return;
        }

        // Rule 2.13: null 체크
        if (mapped == null) {
            upstream.cancel();
            onError(new NullPointerException("Mapper returned null for item: " + item));
            return;
        }

        downstream.onNext(mapped);
    }

    @Override
    public void onError(Throwable t) {
        if (done) {
            return;
        }
        done = true;
        downstream.onError(t);
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        downstream.onComplete();
    }
}
