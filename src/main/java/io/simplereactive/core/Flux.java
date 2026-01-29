package io.simplereactive.core;

import io.simplereactive.operator.FilterOperator;
import io.simplereactive.operator.MapOperator;
import io.simplereactive.operator.TakeOperator;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Operator 체이닝을 지원하는 Publisher 래퍼.
 *
 * <p>기존 Publisher를 감싸서 map, filter, take 등의 연산을
 * 메서드 체이닝으로 사용할 수 있게 합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Flux.from(1, 2, 3, 4, 5)
 *     .map(x -> x * 2)
 *     .filter(x -> x > 5)
 *     .take(2)
 *     .subscribe(subscriber);
 * }</pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * Source:   ──1──2──3──4──5──|
 *               │
 *          map(x*2)
 *               │
 *           ──2──4──6──8──10─|
 *               │
 *         filter(>5)
 *               │
 *           ─────────6──8──10─|
 *               │
 *           take(2)
 *               │
 *           ─────────6──8──|
 * </pre>
 *
 * @param <T> 요소 타입
 */
public class Flux<T> implements Publisher<T> {

    private final Publisher<T> source;

    /**
     * 기존 Publisher를 감싸는 Flux를 생성합니다.
     *
     * @param source 원본 Publisher
     */
    private Flux(Publisher<T> source) {
        this.source = Objects.requireNonNull(source, "Source must not be null");
    }

    // ========== 팩토리 메서드 ==========

    /**
     * 기존 Publisher를 Flux로 변환합니다.
     *
     * @param publisher 원본 Publisher
     * @param <T> 요소 타입
     * @return Flux 인스턴스
     */
    public static <T> Flux<T> from(Publisher<T> publisher) {
        if (publisher instanceof Flux) {
            return (Flux<T>) publisher;
        }
        return new Flux<>(publisher);
    }

    /**
     * 주어진 요소들로 Flux를 생성합니다.
     *
     * @param items 발행할 요소들
     * @param <T> 요소 타입
     * @return Flux 인스턴스
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Flux<T> just(T... items) {
        Objects.requireNonNull(items, "Items must not be null");
        return new Flux<>(new ArrayPublisher<>(items));
    }

    /**
     * 비어있는 Flux를 생성합니다.
     *
     * @param <T> 요소 타입
     * @return 빈 Flux
     */
    public static <T> Flux<T> empty() {
        return new Flux<>(subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        });
    }

    /**
     * 정수 범위로 Flux를 생성합니다.
     *
     * @param start 시작값 (포함)
     * @param count 개수
     * @return 범위 Flux
     */
    public static Flux<Integer> range(int start, int count) {
        if (count <= 0) {
            return empty();
        }
        Integer[] items = new Integer[count];
        for (int i = 0; i < count; i++) {
            items[i] = start + i;
        }
        return new Flux<>(new ArrayPublisher<>(items));
    }

    // ========== Operator 메서드 ==========

    /**
     * 각 요소를 변환합니다.
     *
     * <pre>
     * ──1──2──3──|
     *      │
     * map(x -> x * 2)
     *      │
     * ──2──4──6──|
     * </pre>
     *
     * @param mapper 변환 함수
     * @param <R> 결과 타입
     * @return 변환된 Flux
     */
    public <R> Flux<R> map(Function<? super T, ? extends R> mapper) {
        return new Flux<>(new MapOperator<>(this, mapper));
    }

    /**
     * 조건에 맞는 요소만 통과시킵니다.
     *
     * <pre>
     * ──1──2──3──4──5──|
     *        │
     * filter(x -> x % 2 == 0)
     *        │
     * ─────2─────4─────|
     * </pre>
     *
     * @param predicate 필터 조건
     * @return 필터링된 Flux
     */
    public Flux<T> filter(Predicate<? super T> predicate) {
        return new Flux<>(new FilterOperator<>(this, predicate));
    }

    /**
     * 처음 n개의 요소만 가져옵니다.
     *
     * <pre>
     * ──1──2──3──4──5──|
     *        │
     *    take(3)
     *        │
     * ──1──2──3──|
     * </pre>
     *
     * @param n 가져올 개수
     * @return 제한된 Flux
     */
    public Flux<T> take(long n) {
        return new Flux<>(new TakeOperator<>(this, n));
    }

    // ========== Publisher 구현 ==========

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        source.subscribe(subscriber);
    }

    // ========== 내부 ArrayPublisher ==========

    /**
     * 배열 요소를 발행하는 간단한 Publisher.
     */
    private static class ArrayPublisher<T> implements Publisher<T> {

        private final T[] items;

        @SafeVarargs
        @SuppressWarnings("varargs")
        ArrayPublisher(T... items) {
            this.items = Arrays.copyOf(items, items.length);
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new ArraySubscription<>(subscriber, items));
        }

        static class ArraySubscription<T> implements Subscription {
            private final Subscriber<? super T> subscriber;
            private final T[] items;
            private int index = 0;
            private volatile boolean cancelled = false;

            ArraySubscription(Subscriber<? super T> subscriber, T[] items) {
                this.subscriber = subscriber;
                this.items = items;
            }

            @Override
            public void request(long n) {
                if (cancelled) return;

                long remaining = n;
                while (remaining > 0 && index < items.length && !cancelled) {
                    subscriber.onNext(items[index++]);
                    remaining--;
                }

                if (index >= items.length && !cancelled) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }
}
