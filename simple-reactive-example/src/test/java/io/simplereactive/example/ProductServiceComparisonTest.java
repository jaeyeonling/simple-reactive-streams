package io.simplereactive.example;

import io.simplereactive.core.Publisher;
import io.simplereactive.scheduler.Scheduler;
import io.simplereactive.scheduler.Schedulers;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * LegacyProductService vs ReactiveProductService 비교 테스트.
 */
@DisplayName("ProductService 비교")
class ProductServiceComparisonTest {

    private LegacyProductService legacyService;
    private ReactiveProductService reactiveService;
    private Scheduler ioScheduler;

    @BeforeEach
    void setUp() {
        MockApis.ProductApi productApi = new MockApis.ProductApi();
        MockApis.ReviewApi reviewApi = new MockApis.ReviewApi();
        MockApis.InventoryApi inventoryApi = new MockApis.InventoryApi();

        legacyService = new LegacyProductService(productApi, reviewApi, inventoryApi);
        
        // 독립적인 스케줄러 사용
        ioScheduler = Schedulers.newParallel(4);
        reactiveService = new ReactiveProductService(ioScheduler, productApi, reviewApi, inventoryApi);
    }

    @AfterEach
    void tearDown() {
        legacyService.shutdown();
        ioScheduler.dispose();
    }

    @Nested
    @DisplayName("LegacyProductService")
    class LegacyTests {

        @Test
        @DisplayName("상품 상세 정보를 조회한다")
        void shouldGetProductDetail() {
            // when
            ProductDetail detail = legacyService.getProductDetail("PROD-001");

            // then
            assertThat(detail.product().name()).isEqualTo("Reactive Streams 입문서");
            assertThat(detail.reviews()).hasSize(3);
            assertThat(detail.inventory().quantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 예외를 던진다")
        void shouldThrowOnNotFound() {
            assertThatThrownBy(() -> legacyService.getProductDetail("INVALID"))
                    .isInstanceOf(LegacyProductService.ServiceException.class)
                    .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
        }

        @Test
        @DisplayName("병렬 실행으로 ~300ms 내에 완료된다")
        void shouldCompleteInParallel() {
            // 순차 실행: 200 + 300 + 150 = 650ms
            // 병렬 실행: max(200, 300, 150) = ~300ms

            long start = System.currentTimeMillis();
            legacyService.getProductDetail("PROD-001");
            long elapsed = System.currentTimeMillis() - start;

            // 병렬 실행이므로 500ms 미만 (여유 포함)
            assertThat(elapsed).isLessThan(500);
        }
    }

    @Nested
    @DisplayName("ReactiveProductService")
    class ReactiveTests {

        @Test
        @DisplayName("상품 상세 정보를 조회한다")
        void shouldGetProductDetail() throws Exception {
            // given
            Publisher<ProductDetail> publisher = reactiveService.getProductDetail("PROD-001");
            TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);
            subscriber.await(5, TimeUnit.SECONDS);

            // then
            assertThat(subscriber.getItems()).hasSize(1);
            ProductDetail detail = subscriber.getItems().getFirst();
            assertThat(detail.product().name()).isEqualTo("Reactive Streams 입문서");
            assertThat(detail.reviews()).hasSize(3);
            assertThat(detail.inventory().quantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 onError를 호출한다")
        void shouldErrorOnNotFound() throws Exception {
            // given
            Publisher<ProductDetail> publisher = reactiveService.getProductDetail("INVALID");
            TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);
            subscriber.await(5, TimeUnit.SECONDS);

            // then
            assertThat(subscriber.getError())
                    .isInstanceOf(MockApis.ProductNotFoundException.class);
        }

        @Test
        @DisplayName("fallback이 적용된 조회는 에러 대신 기본값을 반환한다")
        void shouldReturnFallbackOnError() throws Exception {
            // given
            Publisher<ProductDetail> publisher = 
                    reactiveService.getProductDetailWithFallback("INVALID");
            TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);
            subscriber.await(5, TimeUnit.SECONDS);

            // then
            assertThat(subscriber.isCompleted()).isTrue();
            assertThat(subscriber.getError()).isNull();
            
            ProductDetail detail = subscriber.getItems().getFirst();
            assertThat(detail.product().name()).isEqualTo("Unknown Product");
            assertThat(detail.reviews()).isEmpty();
            assertThat(detail.inventory().available()).isFalse();
        }

        @Test
        @DisplayName("병렬 실행으로 완료된다")
        void shouldCompleteInParallel() throws Exception {
            // given
            Publisher<ProductDetail> publisher = reactiveService.getProductDetail("PROD-001");
            TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);
            subscriber.await(10, TimeUnit.SECONDS);

            // then - 완료 확인
            assertThat(subscriber.isCompleted())
                    .describedAs("Should complete. Error: " + subscriber.getError() + ", Items: " + subscriber.getItems())
                    .isTrue();
            assertThat(subscriber.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("결과 비교")
    class ResultComparison {

        @Test
        @DisplayName("두 서비스가 동일한 결과를 반환한다")
        void shouldReturnSameResult() throws Exception {
            // Legacy 결과
            ProductDetail legacyResult = legacyService.getProductDetail("PROD-001");

            // Reactive 결과
            Publisher<ProductDetail> publisher = reactiveService.getProductDetail("PROD-001");
            TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();
            publisher.subscribe(subscriber);
            subscriber.request(1);
            subscriber.await(5, TimeUnit.SECONDS);
            ProductDetail reactiveResult = subscriber.getItems().getFirst();

            // 비교
            assertThat(reactiveResult.product()).isEqualTo(legacyResult.product());
            assertThat(reactiveResult.reviews()).isEqualTo(legacyResult.reviews());
            assertThat(reactiveResult.inventory()).isEqualTo(legacyResult.inventory());
        }
    }
}
