package io.simplereactive.example;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Flux;
import io.simplereactive.operator.ZipOperator;
import io.simplereactive.publisher.DeferPublisher;
import io.simplereactive.scheduler.Scheduler;
import io.simplereactive.scheduler.Schedulers;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Reactive 조합 디버그 테스트.
 */
@DisplayName("Reactive 조합 디버그")
class ReactiveDebugTest {

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = Schedulers.newParallel(4);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.dispose();
        }
    }

    @Test
    @DisplayName("DeferPublisher + subscribeOn 테스트")
    void testDeferPublisherWithSubscribeOn() throws Exception {
        // given
        Publisher<String> publisher = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return "hello";
        })).subscribeOn(scheduler);
        
        TestSubscriber<String> subscriber = new TestSubscriber<>();

        // when
        publisher.subscribe(subscriber);
        subscriber.request(1);
        subscriber.await(5, TimeUnit.SECONDS);

        // then
        assertThat(subscriber.isCompleted()).isTrue();
        assertThat(subscriber.getItems()).containsExactly("hello");
    }

    @Test
    @DisplayName("Zip2 + subscribeOn 테스트")
    void testZip2WithSubscribeOn() throws Exception {
        // given
        Publisher<String> source1 = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return "A";
        })).subscribeOn(scheduler);
        
        Publisher<Integer> source2 = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return 1;
        })).subscribeOn(scheduler);
        
        Publisher<String> result = ZipOperator.zip(source1, source2, (s, i) -> s + i);
        
        TestSubscriber<String> subscriber = new TestSubscriber<>();

        // when
        result.subscribe(subscriber);
        subscriber.request(1);
        subscriber.await(5, TimeUnit.SECONDS);

        // then
        assertThat(subscriber.isCompleted()).isTrue();
        assertThat(subscriber.getItems()).containsExactly("A1");
    }

    @Test
    @DisplayName("Zip3 + subscribeOn 테스트")
    void testZip3WithSubscribeOn() throws Exception {
        // given
        Publisher<String> source1 = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return "A";
        })).subscribeOn(scheduler);
        
        Publisher<Integer> source2 = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return 1;
        })).subscribeOn(scheduler);
        
        Publisher<Boolean> source3 = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(100);
            return true;
        })).subscribeOn(scheduler);
        
        Publisher<String> result = ZipOperator.zip(
                source1, source2, source3,
                (s, i, b) -> s + i + b
        );
        
        TestSubscriber<String> subscriber = new TestSubscriber<>();

        // when
        result.subscribe(subscriber);
        subscriber.request(1);
        subscriber.await(5, TimeUnit.SECONDS);

        // then
        assertThat(subscriber.isCompleted()).isTrue();
        assertThat(subscriber.getItems()).containsExactly("A1true");
    }

    @Test
    @DisplayName("ReactiveProductService와 유사한 패턴 테스트")
    void testProductServicePattern() throws Exception {
        // given - ReactiveProductService와 유사한 패턴
        Publisher<Product> productPublisher = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(200);
            return new Product("PROD-001", "Test Product", 10000);
        })).subscribeOn(scheduler);

        Publisher<List<Review>> reviewsPublisher = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(300);
            return List.of(new Review("PROD-001", "user1", "Good", 5));
        })).subscribeOn(scheduler);

        Publisher<Inventory> inventoryPublisher = Flux.from(new DeferPublisher<>(() -> {
            Thread.sleep(150);
            return new Inventory("PROD-001", 100);
        })).subscribeOn(scheduler);

        Publisher<ProductDetail> result = ZipOperator.zip(
                productPublisher,
                reviewsPublisher,
                inventoryPublisher,
                ProductDetail::new
        );

        TestSubscriber<ProductDetail> subscriber = new TestSubscriber<>();

        // when
        result.subscribe(subscriber);
        subscriber.request(1);
        subscriber.await(5, TimeUnit.SECONDS);

        // then
        assertThat(subscriber.isCompleted()).isTrue();
        assertThat(subscriber.getItems()).hasSize(1);
        ProductDetail detail = subscriber.getItems().getFirst();
        assertThat(detail.product().name()).isEqualTo("Test Product");
        assertThat(detail.reviews()).hasSize(1);
        assertThat(detail.inventory().quantity()).isEqualTo(100);
    }
}
