package io.simplereactive.example;

import java.util.List;
import java.util.Objects;

/**
 * 상품 상세 정보를 나타내는 복합 도메인 모델.
 *
 * <p>Module 9 실습에서 여러 API 결과를 조합한 결과입니다.
 * 레거시 코드에서는 Future.get()으로 블로킹 조합하고,
 * Reactive에서는 ZipOperator로 논블로킹 조합합니다.
 *
 * <h2>조합되는 데이터</h2>
 * <ul>
 *   <li>{@link Product} - 상품 기본 정보 (이름, 가격)</li>
 *   <li>{@link Review} 목록 - 사용자 리뷰들</li>
 *   <li>{@link Inventory} - 재고 정보</li>
 * </ul>
 *
 * @param product   상품 정보
 * @param reviews   리뷰 목록
 * @param inventory 재고 정보
 */
public record ProductDetail(Product product, List<Review> reviews, Inventory inventory) {

    public ProductDetail {
        Objects.requireNonNull(product, "Product must not be null");
        Objects.requireNonNull(reviews, "Reviews must not be null");
        Objects.requireNonNull(inventory, "Inventory must not be null");
    }

    /**
     * 평균 평점을 계산합니다.
     *
     * @return 평균 평점 (리뷰가 없으면 0.0)
     */
    public double averageRating() {
        if (reviews.isEmpty()) {
            return 0.0;
        }
        return reviews.stream()
                .mapToInt(Review::rating)
                .average()
                .orElse(0.0);
    }

    /**
     * 주문 가능 여부를 반환합니다.
     */
    public boolean isAvailable() {
        return inventory.available();
    }
}
