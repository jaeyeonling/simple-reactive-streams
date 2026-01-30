package io.simplereactive.example;

import java.util.Objects;

/**
 * 재고 정보를 나타내는 도메인 모델.
 *
 * <p>Module 9 실습에서 레거시 vs Reactive 비교를 위해 사용합니다.
 *
 * @param productId 상품 ID
 * @param quantity  재고 수량
 * @param available 주문 가능 여부
 */
public record Inventory(String productId, int quantity, boolean available) {

    public Inventory {
        Objects.requireNonNull(productId, "Product id must not be null");
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
    }

    /**
     * 재고 수량으로 Inventory를 생성합니다.
     * quantity > 0이면 available = true입니다.
     */
    public Inventory(String productId, int quantity) {
        this(productId, quantity, quantity > 0);
    }
}
