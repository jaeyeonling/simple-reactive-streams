package io.simplereactive.example;

import io.simplereactive.core.Flux;
import io.simplereactive.scheduler.Scheduler;
import io.simplereactive.scheduler.Schedulers;

import java.util.List;

/**
 * Reactive 방식 상품 서비스.
 *
 * <p>Flux.defer()와 Flux.zip()을 사용하여 논블로킹으로 API를 조합합니다.
 * {@link LegacyProductService}의 문제점을 해결합니다.
 *
 * <h2>장점</h2>
 * <ol>
 *   <li><strong>논블로킹</strong>: 스레드가 대기하지 않음</li>
 *   <li><strong>선언적 조합</strong>: Flux.zip()으로 깔끔하게 조합</li>
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
 *               └──> Flux.zip()이 모든 결과 조합
 *                        │
 *                        ▼
 *                   onNext(ProductDetail)
 * </pre>
 *
 * @see LegacyProductService 레거시 방식 비교
 * @see Flux#defer
 * @see Flux#zip
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
     * 세 API가 병렬로 실행되며, Flux.zip()이 결과를 조합합니다.
     *
     * <h3>Marble Diagram</h3>
     * <pre>
     * getProduct:    ──────────(P)──|
     * getReviews:    ────────────────(R)──|
     * getInventory:  ────(I)──|
     *                    │
     *               Flux.zip((p,r,i) -> detail)
     *                    │
     * result:       ────────────────(detail)──|
     * </pre>
     *
     * @param productId 상품 ID
     * @return ProductDetail Flux
     */
    public Flux<ProductDetail> getProductDetail(String productId) {
        var product = Flux.defer(() -> productApi.getProduct(productId))
                .subscribeOn(ioScheduler);

        var reviews = Flux.defer(() -> reviewApi.getReviews(productId))
                .subscribeOn(ioScheduler);

        var inventory = Flux.defer(() -> inventoryApi.getInventory(productId))
                .subscribeOn(ioScheduler);

        return Flux.zip(product, reviews, inventory, ProductDetail::new);
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
     * @return ProductDetail Flux (에러 복구 적용)
     */
    public Flux<ProductDetail> getProductDetailWithFallback(String productId) {
        var product = Flux.defer(() -> productApi.getProduct(productId))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> new Product(productId, "Unknown Product", 0));

        var reviews = Flux.<List<Review>>defer(() -> reviewApi.getReviews(productId))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> List.of());

        var inventory = Flux.defer(() -> inventoryApi.getInventory(productId))
                .subscribeOn(ioScheduler)
                .onErrorReturn(e -> new Inventory(productId, 0, false));

        return Flux.zip(product, reviews, inventory, ProductDetail::new);
    }

    /**
     * 서비스를 종료합니다.
     */
    public void shutdown() {
        ioScheduler.dispose();
    }
}
