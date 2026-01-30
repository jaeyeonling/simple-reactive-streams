package io.simplereactive.core;

/**
 * 세 개의 인자를 받아 결과를 반환하는 함수형 인터페이스.
 *
 * <p>Java 표준 라이브러리에는 {@link java.util.function.BiFunction}까지만 제공되므로,
 * 세 개 이상의 Publisher를 조합할 때 사용할 수 있도록 제공합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * TriFunction<Product, List<Review>, Inventory, ProductDetail> combiner =
 *     (product, reviews, inventory) -> new ProductDetail(product, reviews, inventory);
 *
 * // Flux.zip과 함께 사용
 * Flux.zip(productPub, reviewsPub, inventoryPub, ProductDetail::new);
 * }</pre>
 *
 * @param <T1> 첫 번째 인자 타입
 * @param <T2> 두 번째 인자 타입
 * @param <T3> 세 번째 인자 타입
 * @param <R> 결과 타입
 * @see Flux#zip(Publisher, Publisher, Publisher, TriFunction)
 */
@FunctionalInterface
public interface TriFunction<T1, T2, T3, R> {

    /**
     * 주어진 인자들에 함수를 적용합니다.
     *
     * @param t1 첫 번째 인자
     * @param t2 두 번째 인자
     * @param t3 세 번째 인자
     * @return 함수 적용 결과
     */
    R apply(T1 t1, T2 t2, T3 t3);
}
