package io.simplereactive.example;

import io.simplereactive.core.Flux;
import io.simplereactive.core.Publisher;
import io.simplereactive.operator.ZipOperator;
import io.simplereactive.publisher.DeferPublisher;
import io.simplereactive.scheduler.Scheduler;
import io.simplereactive.scheduler.Schedulers;

import java.util.List;

/**
 * Reactive 방식 상품 서비스.
 *
 * <p>DeferPublisher와 ZipOperator를 사용하여 논블로킹으로 API를 조합합니다.
 * {@link LegacyProductService}의 문제점을 해결합니다.
 *
 * <h2>장점</h2>
 * <ol>
 *   <li><strong>논블로킹</strong>: 스레드가 대기하지 않음</li>
 *   <li><strong>선언적 조합</strong>: ZipOperator로 깔끔하게 조합</li>
 *   <li><strong>에러 처리</strong>: onErrorResume으로 우아한 복구</li>
 *   <li><strong>자동 취소</strong>: 하나가 실패하면 나머지 자동 취소</li>
 *   <li><strong>Scheduler 추상화</strong>: I/O 스레드 분리 용이</li>
 * </ol>
 *
 * <h2>동작 방식</h2>
 * <pre>
 * [Main Thread]
 *      │
 *      └──> subscribe()
 *               │
 *               ├──> [IO-1] productApi.getProduct()
 *               ├──> [IO-2] reviewApi.getReviews()
 *               ├──> [IO-3] inventoryApi.getInventory()
 *               │
 *               └──> ZipOperator가 모든 결과 조합
 *                        │
 *                        ▼
 *                   onNext(ProductDetail)
 * </pre>
 *
 * @see LegacyProductService 레거시 방식 비교
 * @see DeferPublisher
 * @see ZipOperator
 */
public class ReactiveProductService {

    private final Scheduler ioScheduler;
    private final MockApis.ProductApi productApi;
    private final MockApis.ReviewApi reviewApi;
    private final MockApis.InventoryApi inventoryApi;

    /**
     * 기본 Parallel Scheduler로 서비스를 생성합니다.
     */
    public ReactiveProductService(MockApis.ProductApi productApi,
                                  MockApis.ReviewApi reviewApi,
                                  MockApis.InventoryApi inventoryApi) {
        this(Schedulers.parallel(), productApi, reviewApi, inventoryApi);
    }

    /**
     * 지정된 Scheduler로 서비스를 생성합니다.
     */
    public ReactiveProductService(Scheduler ioScheduler,
                                  MockApis.ProductApi productApi,
                                  MockApis.ReviewApi reviewApi,
                                  MockApis.InventoryApi inventoryApi) {
        this.ioScheduler = ioScheduler;
        this.productApi = productApi;
        this.reviewApi = reviewApi;
        this.inventoryApi = inventoryApi;
    }

    /**
     * 상품 상세 정보를 조회합니다 (논블로킹).
     *
     * <p>Publisher를 반환하므로 subscribe() 전까지 실행되지 않습니다.
     * 세 API가 병렬로 실행되며, ZipOperator가 결과를 조합합니다.
     *
     * <h3>Marble Diagram</h3>
     * <pre>
     * getProduct:    ──────────(P)──|
     * getReviews:    ────────────────(R)──|
     * getInventory:  ────(I)──|
     *                    │
     *               zip((p,r,i) -> detail)
     *                    │
     * result:       ────────────────(detail)──|
     * </pre>
     *
     * @param productId 상품 ID
     * @return ProductDetail Publisher
     */
    public Publisher<ProductDetail> getProductDetail(String productId) {
        return ZipOperator.zip(
                getProduct(productId),
                getReviews(productId),
                getInventory(productId),
                ProductDetail::new
        );
    }

    /**
     * 에러 복구가 적용된 상품 상세 정보를 조회합니다.
     *
     * <p>각 API 호출에 대해 에러 복구 전략이 적용됩니다:
     * <ul>
     *   <li>Product 실패: 기본 상품 반환</li>
     *   <li>Reviews 실패: 빈 리스트 반환</li>
     *   <li>Inventory 실패: 재고 없음 반환</li>
     * </ul>
     *
     * @param productId 상품 ID
     * @return ProductDetail Publisher (에러 복구 적용)
     */
    public Publisher<ProductDetail> getProductDetailWithFallback(String productId) {
        return ZipOperator.zip(
                getProductWithFallback(productId),
                getReviewsWithFallback(productId),
                getInventoryWithFallback(productId),
                ProductDetail::new
        );
    }

    /**
     * 서비스를 종료합니다.
     */
    public void shutdown() {
        ioScheduler.dispose();
    }

    // ========== Private API Wrappers ==========

    private Publisher<Product> getProduct(String productId) {
        // 블로킹 API를 Publisher로 래핑하고, I/O 스레드에서 실행
        return Flux.from(new DeferPublisher<>(() ->
                        productApi.getProduct(productId)))
                .subscribeOn(ioScheduler);
    }

    private Publisher<List<Review>> getReviews(String productId) {
        return Flux.from(new DeferPublisher<>(() ->
                        reviewApi.getReviews(productId)))
                .subscribeOn(ioScheduler);
    }

    private Publisher<Inventory> getInventory(String productId) {
        return Flux.from(new DeferPublisher<>(() ->
                        inventoryApi.getInventory(productId)))
                .subscribeOn(ioScheduler);
    }

    // ========== Fallback Versions ==========

    private Publisher<Product> getProductWithFallback(String productId) {
        return Flux.from(new DeferPublisher<>(() ->
                        productApi.getProduct(productId)))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> new Product(productId, "Unknown Product", 0));
    }

    private Publisher<List<Review>> getReviewsWithFallback(String productId) {
        return Flux.from(new DeferPublisher<>(() ->
                        reviewApi.getReviews(productId)))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> List.of());
    }

    private Publisher<Inventory> getInventoryWithFallback(String productId) {
        return Flux.from(new DeferPublisher<>(() ->
                        inventoryApi.getInventory(productId)))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> new Inventory(productId, 0, false));
    }
}
