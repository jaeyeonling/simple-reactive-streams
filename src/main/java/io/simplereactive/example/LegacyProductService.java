package io.simplereactive.example;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Thread 기반 레거시 상품 서비스.
 *
 * <p>ExecutorService와 Future를 사용하여 병렬로 API를 호출합니다.
 * 이 방식의 문제점을 이해하고, Reactive 방식과 비교하기 위한 예제입니다.
 *
 * <h2>문제점</h2>
 * <ol>
 *   <li><strong>스레드 자원 낭비</strong>: 고정 크기 스레드 풀로 인해 대기 발생</li>
 *   <li><strong>블로킹</strong>: Future.get()에서 스레드가 블로킹됨</li>
 *   <li><strong>에러 처리 어려움</strong>: try-catch로 감싸야 하며, 어떤 API가 실패했는지 구분 어려움</li>
 *   <li><strong>취소 어려움</strong>: 하나가 실패해도 나머지 Future를 수동으로 취소해야 함</li>
 * </ol>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * [Main Thread]
 *      │
 *      ├──> submit(productApi) ──> [Thread-1] 블로킹 200ms
 *      ├──> submit(reviewApi)  ──> [Thread-2] 블로킹 300ms
 *      ├──> submit(inventoryApi) ──> [Thread-3] 블로킹 150ms
 *      │
 *      │ (Main Thread도 Future.get()에서 블로킹!)
 *      │
 *      ▼
 *    결과 조합
 * </pre>
 *
 * @see ReactiveProductService Reactive 방식 비교
 */
public class LegacyProductService {

    private final ExecutorService executor;
    private final MockApis.ProductApi productApi;
    private final MockApis.ReviewApi reviewApi;
    private final MockApis.InventoryApi inventoryApi;

    /**
     * 기본 스레드 풀(10개)로 서비스를 생성합니다.
     */
    public LegacyProductService(MockApis.ProductApi productApi,
                                MockApis.ReviewApi reviewApi,
                                MockApis.InventoryApi inventoryApi) {
        this(Executors.newFixedThreadPool(10), productApi, reviewApi, inventoryApi);
    }

    /**
     * 지정된 ExecutorService로 서비스를 생성합니다.
     */
    public LegacyProductService(ExecutorService executor,
                                MockApis.ProductApi productApi,
                                MockApis.ReviewApi reviewApi,
                                MockApis.InventoryApi inventoryApi) {
        this.executor = executor;
        this.productApi = productApi;
        this.reviewApi = reviewApi;
        this.inventoryApi = inventoryApi;
    }

    /**
     * 상품 상세 정보를 조회합니다 (블로킹).
     *
     * <p>세 API를 병렬로 호출하지만, Future.get()에서 블로킹됩니다.
     *
     * <h3>소요 시간</h3>
     * <ul>
     *   <li>ProductApi: 200ms</li>
     *   <li>ReviewApi: 300ms (병목)</li>
     *   <li>InventoryApi: 150ms</li>
     * </ul>
     * <p>병렬 실행으로 총 ~300ms 소요 (가장 느린 API 기준)
     *
     * @param productId 상품 ID
     * @return 상품 상세 정보
     * @throws ServiceException API 호출 실패 시
     */
    public ProductDetail getProductDetail(String productId) {
        // 각 API 호출을 별도 스레드에서 실행
        Future<Product> productFuture = executor.submit(() ->
                productApi.getProduct(productId)
        );
        Future<List<Review>> reviewsFuture = executor.submit(() ->
                reviewApi.getReviews(productId)
        );
        Future<Inventory> inventoryFuture = executor.submit(() ->
                inventoryApi.getInventory(productId)
        );

        try {
            // 모든 결과를 기다림 (블로킹!)
            // 문제: 호출 스레드가 여기서 대기하며 아무 일도 못함
            Product product = productFuture.get(1, TimeUnit.SECONDS);
            List<Review> reviews = reviewsFuture.get(1, TimeUnit.SECONDS);
            Inventory inventory = inventoryFuture.get(1, TimeUnit.SECONDS);

            return new ProductDetail(product, reviews, inventory);
        } catch (Exception e) {
            // 문제: 어떤 API가 실패했는지 알기 어려움
            // 문제: 하나가 실패해도 나머지 Future는 계속 실행됨 (자원 낭비)
            cancelAll(productFuture, reviewsFuture, inventoryFuture);
            throw new ServiceException("Failed to get product detail: " + productId, e);
        }
    }

    /**
     * 서비스를 종료합니다.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void cancelAll(Future<?>... futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    /**
     * 서비스 예외.
     */
    public static class ServiceException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public ServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
