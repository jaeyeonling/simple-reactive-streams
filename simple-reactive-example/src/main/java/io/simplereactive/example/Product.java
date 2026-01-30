package io.simplereactive.example;

import java.util.Objects;

/**
 * 상품 정보를 나타내는 도메인 모델.
 *
 * <p>Module 9 실습에서 레거시 vs Reactive 비교를 위해 사용합니다.
 *
 * @param id    상품 ID
 * @param name  상품명
 * @param price 가격
 */
public record Product(String id, String name, int price) {

    public Product {
        Objects.requireNonNull(id, "Product id must not be null");
        Objects.requireNonNull(name, "Product name must not be null");
        if (price < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
    }
}
