package io.simplereactive.example;

import java.util.Objects;

/**
 * 상품 리뷰를 나타내는 도메인 모델.
 *
 * <p>Module 9 실습에서 레거시 vs Reactive 비교를 위해 사용합니다.
 *
 * @param productId 상품 ID
 * @param author    작성자
 * @param content   리뷰 내용
 * @param rating    평점 (1-5)
 */
public record Review(String productId, String author, String content, int rating) {

    public Review {
        Objects.requireNonNull(productId, "Product id must not be null");
        Objects.requireNonNull(author, "Author must not be null");
        Objects.requireNonNull(content, "Content must not be null");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
    }
}
