package io.simplereactive.example;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock API 클래스들.
 *
 * <p>Module 9 실습에서 실제 외부 API 호출을 시뮬레이션합니다.
 * 각 API는 지연 시간을 가지며, 블로킹 호출입니다.
 *
 * <h2>시뮬레이션되는 지연 시간</h2>
 * <ul>
 *   <li>ProductApi: 200ms</li>
 *   <li>ReviewApi: 300ms</li>
 *   <li>InventoryApi: 150ms</li>
 * </ul>
 *
 * <p>순차 호출 시 총 650ms, 병렬 호출 시 ~300ms (가장 느린 API 기준)
 */
public final class MockApis {

    private MockApis() {
        // utility class
    }

    /**
     * 상품 API Mock.
     *
     * <p>지연 시간: 200ms
     */
    public static class ProductApi {

        private static final long DELAY_MS = 200;
        private final Map<String, Product> products = new ConcurrentHashMap<>();

        public ProductApi() {
            // 기본 테스트 데이터
            products.put("PROD-001", new Product("PROD-001", "Reactive Streams 입문서", 35000));
            products.put("PROD-002", new Product("PROD-002", "Java 동시성 프로그래밍", 42000));
            products.put("PROD-003", new Product("PROD-003", "함수형 프로그래밍", 38000));
        }

        /**
         * 상품을 추가합니다 (테스트용).
         */
        public void addProduct(Product product) {
            products.put(product.id(), product);
        }

        /**
         * 상품 정보를 조회합니다 (블로킹).
         *
         * @param productId 상품 ID
         * @return 상품 정보
         * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
         */
        public Product getProduct(String productId) {
            simulateDelay(DELAY_MS);

            Product product = products.get(productId);
            if (product == null) {
                throw new ProductNotFoundException("Product not found: " + productId);
            }
            return product;
        }
    }

    /**
     * 리뷰 API Mock.
     *
     * <p>지연 시간: 300ms (가장 느림)
     */
    public static class ReviewApi {

        private static final long DELAY_MS = 300;
        private final Map<String, List<Review>> reviews = new ConcurrentHashMap<>();

        public ReviewApi() {
            // 기본 테스트 데이터
            reviews.put("PROD-001", List.of(
                    new Review("PROD-001", "user1", "정말 좋은 책입니다!", 5),
                    new Review("PROD-001", "user2", "입문자에게 추천", 4),
                    new Review("PROD-001", "user3", "설명이 잘 되어있어요", 5)
            ));
            reviews.put("PROD-002", List.of(
                    new Review("PROD-002", "dev1", "실무에 도움됨", 4)
            ));
            reviews.put("PROD-003", List.of());
        }

        /**
         * 리뷰를 추가합니다 (테스트용).
         */
        public void setReviews(String productId, List<Review> reviewList) {
            reviews.put(productId, reviewList);
        }

        /**
         * 상품의 리뷰 목록을 조회합니다 (블로킹).
         *
         * @param productId 상품 ID
         * @return 리뷰 목록 (없으면 빈 리스트)
         */
        public List<Review> getReviews(String productId) {
            simulateDelay(DELAY_MS);
            return reviews.getOrDefault(productId, List.of());
        }
    }

    /**
     * 재고 API Mock.
     *
     * <p>지연 시간: 150ms (가장 빠름)
     */
    public static class InventoryApi {

        private static final long DELAY_MS = 150;
        private final Map<String, Inventory> inventories = new ConcurrentHashMap<>();

        public InventoryApi() {
            // 기본 테스트 데이터
            inventories.put("PROD-001", new Inventory("PROD-001", 100));
            inventories.put("PROD-002", new Inventory("PROD-002", 5));
            inventories.put("PROD-003", new Inventory("PROD-003", 0)); // 품절
        }

        /**
         * 재고를 설정합니다 (테스트용).
         */
        public void setInventory(Inventory inventory) {
            inventories.put(inventory.productId(), inventory);
        }

        /**
         * 재고 정보를 조회합니다 (블로킹).
         *
         * @param productId 상품 ID
         * @return 재고 정보
         */
        public Inventory getInventory(String productId) {
            simulateDelay(DELAY_MS);
            return inventories.getOrDefault(productId, new Inventory(productId, 0));
        }
    }

    /**
     * 상품을 찾을 수 없을 때 발생하는 예외.
     */
    public static class ProductNotFoundException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public ProductNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * API 지연을 시뮬레이션합니다.
     */
    private static void simulateDelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("API call interrupted", e);
        }
    }
}
