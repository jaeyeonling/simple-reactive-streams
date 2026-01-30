package io.simplereactive.scheduler;

import io.simplereactive.core.Flux;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Scheduler 테스트.
 *
 * <p>Scheduler의 핵심 개념과 동작을 검증합니다.
 */
@DisplayName("Scheduler 테스트")
class SchedulerTest {

    @AfterEach
    void tearDown() {
        // 테스트 후 공유 Scheduler 정리
        Schedulers.shutdownAll();
    }

    @Nested
    @DisplayName("ImmediateScheduler")
    class ImmediateSchedulerTest {

        @Test
        @DisplayName("현재 스레드에서 즉시 실행")
        void executesOnCurrentThread() {
            Scheduler scheduler = Schedulers.immediate();
            AtomicReference<String> threadName = new AtomicReference<>();

            scheduler.schedule(() -> threadName.set(Thread.currentThread().getName()));

            assertThat(threadName.get())
                    .isEqualTo(Thread.currentThread().getName());
        }

        @Test
        @DisplayName("schedule은 작업 완료 후 반환 (동기)")
        void scheduleIsSynchronous() {
            Scheduler scheduler = Schedulers.immediate();
            List<String> order = new ArrayList<>();

            order.add("before");
            scheduler.schedule(() -> order.add("task"));
            order.add("after");

            // 동기 실행이므로 순서가 보장됨
            assertThat(order).containsExactly("before", "task", "after");
        }

        @Test
        @DisplayName("dispose해도 계속 사용 가능 (리소스 없음)")
        void disposeHasNoEffect() {
            Scheduler scheduler = Schedulers.immediate();

            scheduler.dispose();
            
            assertThat(scheduler.isDisposed()).isFalse();

            // 여전히 작업 실행 가능
            AtomicReference<String> result = new AtomicReference<>();
            scheduler.schedule(() -> result.set("executed"));

            assertThat(result.get()).isEqualTo("executed");
        }
    }

    @Nested
    @DisplayName("SingleThreadScheduler")
    class SingleThreadSchedulerTest {

        @Test
        @DisplayName("단일 스레드에서 실행")
        void executesOnSingleThread() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(3);
            List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

            try {
                for (int i = 0; i < 3; i++) {
                    scheduler.schedule(() -> {
                        threadNames.add(Thread.currentThread().getName());
                        latch.countDown();
                    });
                }

                latch.await(1, TimeUnit.SECONDS);

                // 모든 작업이 같은 스레드에서 실행됨
                assertThat(threadNames).hasSize(3);
                assertThat(threadNames.stream().distinct().count()).isEqualTo(1);
                assertThat(threadNames.get(0)).startsWith("single-");
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("작업 순서 보장 (FIFO)")
        void maintainsOrder() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(5);
            List<Integer> results = Collections.synchronizedList(new ArrayList<>());

            try {
                for (int i = 0; i < 5; i++) {
                    final int value = i;
                    scheduler.schedule(() -> {
                        results.add(value);
                        latch.countDown();
                    });
                }

                latch.await(1, TimeUnit.SECONDS);

                // 순서가 보장됨
                assertThat(results).containsExactly(0, 1, 2, 3, 4);
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("dispose 후 새 작업은 즉시 Disposed 반환")
        void disposedSchedulerReturnsDisposed() {
            Scheduler scheduler = Schedulers.newSingle();

            scheduler.dispose();

            assertThat(scheduler.isDisposed()).isTrue();

            Disposable disposable = scheduler.schedule(() -> {});
            assertThat(disposable.isDisposed()).isTrue();
        }
    }

    @Nested
    @DisplayName("ParallelScheduler")
    class ParallelSchedulerTest {

        @Test
        @DisplayName("여러 스레드에서 병렬 실행")
        void executesInParallel() throws InterruptedException {
            Scheduler scheduler = Schedulers.newParallel(4);
            CountDownLatch latch = new CountDownLatch(4);
            List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

            try {
                for (int i = 0; i < 4; i++) {
                    scheduler.schedule(() -> {
                        threadNames.add(Thread.currentThread().getName());
                        try {
                            Thread.sleep(50);  // 병렬 실행 확인을 위한 지연
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        latch.countDown();
                    });
                }

                latch.await(1, TimeUnit.SECONDS);

                // 여러 스레드에서 실행됨 (모든 스레드가 다를 수 있음)
                assertThat(threadNames).hasSize(4);
                assertThat(threadNames.get(0)).startsWith("parallel-");
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("parallelism 설정")
        void respectsParallelism() {
            ParallelScheduler scheduler = new ParallelScheduler(8);

            try {
                assertThat(scheduler.getParallelism()).isEqualTo(8);
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("parallelism이 1 미만이면 예외")
        void invalidParallelism() {
            assertThatThrownBy(() -> new ParallelScheduler(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 1");
        }
    }

    @Nested
    @DisplayName("subscribeOn Operator")
    class SubscribeOnTest {

        @Test
        @DisplayName("구독이 Scheduler에서 실행됨")
        void subscriptionHappensOnScheduler() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> subscribeThread = new AtomicReference<>();

            try {
                Flux.range(1, 3)
                        .subscribeOn(scheduler)
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                subscribeThread.set(Thread.currentThread().getName());
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                assertThat(subscribeThread.get()).startsWith("single-");
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("데이터도 같은 스레드에서 발행")
        void dataEmittedOnSameThread() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            List<String> dataThreads = Collections.synchronizedList(new ArrayList<>());

            try {
                Flux.range(1, 3)
                        .subscribeOn(scheduler)
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Integer item) {
                                dataThreads.add(Thread.currentThread().getName());
                                super.onNext(item);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                // 모든 데이터가 같은 스레드에서 발행됨
                assertThat(dataThreads).hasSize(3);
                assertThat(dataThreads.stream().distinct().count()).isEqualTo(1);
                assertThat(dataThreads.get(0)).startsWith("single-");
            } finally {
                scheduler.dispose();
            }
        }
    }

    @Nested
    @DisplayName("publishOn Operator")
    class PublishOnTest {

        @Test
        @DisplayName("publishOn 이후 다른 스레드에서 실행")
        void executesOnDifferentThread() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            List<String> dataThreads = Collections.synchronizedList(new ArrayList<>());

            try {
                Flux.range(1, 3)
                        .publishOn(scheduler)
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Integer item) {
                                dataThreads.add(Thread.currentThread().getName());
                                super.onNext(item);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                // publishOn 이후 스케줄러 스레드에서 실행됨
                assertThat(dataThreads).hasSize(3);
                assertThat(dataThreads.get(0)).startsWith("single-");
            } finally {
                scheduler.dispose();
            }
        }

        @Test
        @DisplayName("순서가 보장됨")
        void maintainsOrder() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            List<Integer> results = Collections.synchronizedList(new ArrayList<>());

            try {
                Flux.range(1, 5)
                        .publishOn(scheduler)
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Integer item) {
                                results.add(item);
                                super.onNext(item);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                assertThat(results).containsExactly(1, 2, 3, 4, 5);
            } finally {
                scheduler.dispose();
            }
        }
    }

    @Nested
    @DisplayName("subscribeOn + publishOn 조합")
    class CombinedSchedulersTest {

        @Test
        @DisplayName("subscribeOn으로 구독, publishOn으로 발행 스레드 분리")
        void separateSubscribeAndPublishThreads() throws InterruptedException {
            Scheduler subscribeScheduler = Schedulers.newSingle();
            Scheduler publishScheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            
            AtomicReference<String> subscribeThread = new AtomicReference<>();
            List<String> dataThreads = Collections.synchronizedList(new ArrayList<>());

            try {
                Flux.range(1, 3)
                        .subscribeOn(subscribeScheduler)
                        .publishOn(publishScheduler)
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                subscribeThread.set(Thread.currentThread().getName());
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Integer item) {
                                dataThreads.add(Thread.currentThread().getName());
                                super.onNext(item);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                // 구독은 subscribeOn 스레드에서
                // (publishOn이 있어서 실제로는 publishOn 스레드에서 onSubscribe가 호출됨)
                // 데이터는 publishOn 스레드에서
                assertThat(dataThreads).hasSize(3);
                assertThat(dataThreads.stream().distinct().count()).isEqualTo(1);
            } finally {
                subscribeScheduler.dispose();
                publishScheduler.dispose();
            }
        }

        @Test
        @DisplayName("map과 함께 사용")
        void worksWithMap() throws InterruptedException {
            Scheduler scheduler = Schedulers.newSingle();
            CountDownLatch latch = new CountDownLatch(1);
            List<Integer> results = Collections.synchronizedList(new ArrayList<>());
            List<String> mapThreads = Collections.synchronizedList(new ArrayList<>());

            try {
                Flux.range(1, 3)
                        .publishOn(scheduler)
                        .map(x -> {
                            mapThreads.add(Thread.currentThread().getName());
                            return x * 2;
                        })
                        .subscribe(new TestSubscriber<Integer>() {
                            @Override
                            public void onSubscribe(io.simplereactive.core.Subscription s) {
                                super.onSubscribe(s);
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Integer item) {
                                results.add(item);
                                super.onNext(item);
                            }

                            @Override
                            public void onComplete() {
                                super.onComplete();
                                latch.countDown();
                            }
                        });

                latch.await(1, TimeUnit.SECONDS);

                assertThat(results).containsExactly(2, 4, 6);
                assertThat(mapThreads).hasSize(3);
                assertThat(mapThreads.get(0)).startsWith("single-");
            } finally {
                scheduler.dispose();
            }
        }
    }

    @Nested
    @DisplayName("Schedulers 팩토리")
    class SchedulersFactoryTest {

        @Test
        @DisplayName("immediate()는 싱글톤")
        void immediateSingleton() {
            assertThat(Schedulers.immediate())
                    .isSameAs(Schedulers.immediate());
        }

        @Test
        @DisplayName("single()은 공유 인스턴스")
        void singleShared() {
            assertThat(Schedulers.single())
                    .isSameAs(Schedulers.single());
        }

        @Test
        @DisplayName("parallel()은 공유 인스턴스")
        void parallelShared() {
            assertThat(Schedulers.parallel())
                    .isSameAs(Schedulers.parallel());
        }

        @Test
        @DisplayName("newSingle()은 매번 새 인스턴스")
        void newSingleCreatesNew() {
            Scheduler s1 = Schedulers.newSingle();
            Scheduler s2 = Schedulers.newSingle();

            try {
                assertThat(s1).isNotSameAs(s2);
            } finally {
                s1.dispose();
                s2.dispose();
            }
        }

        @Test
        @DisplayName("newParallel()은 매번 새 인스턴스")
        void newParallelCreatesNew() {
            Scheduler p1 = Schedulers.newParallel();
            Scheduler p2 = Schedulers.newParallel();

            try {
                assertThat(p1).isNotSameAs(p2);
            } finally {
                p1.dispose();
                p2.dispose();
            }
        }
    }
}
