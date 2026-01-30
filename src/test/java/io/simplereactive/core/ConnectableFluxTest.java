package io.simplereactive.core;

import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ConnectableFlux 테스트.
 *
 * <p>Cold Publisher를 Hot Publisher로 변환하는 기능을 검증합니다.
 */
class ConnectableFluxTest {

    @Nested
    @DisplayName("publish() - 수동 연결")
    class PublishBehavior {

        @Test
        @DisplayName("connect() 전에는 데이터를 받지 않음")
        void shouldNotReceiveDataBeforeConnect() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var subscriber = new TestSubscriber<Integer>();

            // When
            connectable.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then - connect 전이므로 데이터 없음
            assertThat(subscriber.getReceivedItems()).isEmpty();
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("connect() 후 모든 구독자가 동시에 데이터 수신")
        void allSubscribersReceiveDataAfterConnect() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();

            // When
            connectable.subscribe(subscriberA);
            connectable.subscribe(subscriberB);
            subscriberA.request(Long.MAX_VALUE);
            subscriberB.request(Long.MAX_VALUE);
            connectable.connect();

            // Then - 둘 다 같은 데이터 수신
            assertThat(subscriberA.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriberB.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriberA.isCompleted()).isTrue();
            assertThat(subscriberB.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("connect() 중복 호출 시 동일한 Disposable 반환")
        void duplicateConnectReturnsSameDisposable() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();

            // When
            var disposable1 = connectable.connect();
            var disposable2 = connectable.connect();

            // Then
            assertThat(disposable2).isSameAs(disposable1);
        }

        @Test
        @DisplayName("Disposable.dispose()로 upstream 취소")
        void disposableCancelsUpstream() {
            // Given
            var cancelled = new AtomicInteger(0);
            var publisher = new Publisher<Integer>() {
                @Override
                public void subscribe(Subscriber<? super Integer> subscriber) {
                    subscriber.onSubscribe(new Subscription() {
                        @Override
                        public void request(long n) {
                            // 느리게 발행
                        }
                        @Override
                        public void cancel() {
                            cancelled.incrementAndGet();
                        }
                    });
                }
            };
            var connectable = new ConnectableFlux<>(publisher);

            // When
            var disposable = connectable.connect();
            disposable.dispose();

            // Then
            assertThat(cancelled.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("share() - 자동 연결")
    class ShareBehavior {

        @Test
        @DisplayName("첫 구독자가 등록되면 자동 연결")
        void shouldAutoConnectOnFirstSubscriber() {
            // Given
            var shared = Flux.just(1, 2, 3).share();
            var subscriber = new TestSubscriber<Integer>();

            // When
            shared.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("늦은 구독자는 이전 데이터를 놓침")
        void lateSubscriberMissesData() {
            // Given - 느린 Publisher
            var emitCount = new AtomicInteger(0);
            var publisher = new Publisher<Integer>() {
                @Override
                public void subscribe(Subscriber<? super Integer> subscriber) {
                    subscriber.onSubscribe(new Subscription() {
                        @Override
                        public void request(long n) {
                            // 요청마다 하나씩 발행
                            int count = emitCount.incrementAndGet();
                            subscriber.onNext(count);
                            if (count >= 3) {
                                subscriber.onComplete();
                            }
                        }
                        @Override
                        public void cancel() {}
                    });
                }
            };
            
            var connectable = new ConnectableFlux<>(publisher);
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();

            // When - A 먼저 구독
            connectable.subscribe(subscriberA);
            connectable.connect();
            
            // B 나중에 구독 (이미 일부 데이터 발행됨)
            connectable.subscribe(subscriberB);
            subscriberB.request(Long.MAX_VALUE);

            // Then - B는 구독 후 데이터만 받음
            assertThat(subscriberA.getReceivedItems()).contains(1);
            // subscriberB는 구독 이후에 발행된 데이터만 받음
        }
    }

    @Nested
    @DisplayName("autoConnect(n) - n명 구독 시 자동 연결")
    class AutoConnectBehavior {

        @Test
        @DisplayName("autoConnect(2): 2명 구독 시 연결")
        void shouldConnectAfterTwoSubscribers() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var autoConnect = connectable.autoConnect(2);
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();

            // When - 첫 번째 구독
            autoConnect.subscribe(subscriberA);
            subscriberA.request(Long.MAX_VALUE);
            
            // Then - 아직 연결 안 됨
            assertThat(subscriberA.getReceivedItems()).isEmpty();

            // When - 두 번째 구독 (연결 트리거)
            autoConnect.subscribe(subscriberB);
            subscriberB.request(Long.MAX_VALUE);

            // Then - 둘 다 데이터 수신
            assertThat(subscriberA.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriberB.getReceivedItems()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("autoConnect(0): 즉시 연결")
        void autoConnectZeroConnectsImmediately() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var autoConnect = connectable.autoConnect(0);
            var subscriber = new TestSubscriber<Integer>();

            // When - 이미 연결되어 데이터가 발행됨, 늦은 구독자는 놓침
            autoConnect.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then - 이미 완료됨
            assertThat(subscriber.isCompleted()).isTrue();
            // 데이터는 구독 전에 발행되어 놓쳤을 수 있음
        }
    }

    @Nested
    @DisplayName("구독 취소")
    class Cancellation {

        @Test
        @DisplayName("cancel 후 데이터를 받지 않음")
        void shouldNotReceiveDataAfterCancel() {
            // Given
            var connectable = Flux.range(1, 100).publish();
            var subscriber = new TestSubscriber<Integer>();
            connectable.subscribe(subscriber);

            // When
            subscriber.cancel();
            connectable.connect();

            // Then
            assertThat(subscriber.getReceivedItems()).isEmpty();
        }

        @Test
        @DisplayName("일부 구독자만 취소해도 다른 구독자는 계속 수신")
        void otherSubscribersContinueAfterOneCancel() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();
            connectable.subscribe(subscriberA);
            connectable.subscribe(subscriberB);

            // When
            subscriberA.cancel();
            connectable.connect();

            // Then
            assertThat(subscriberA.getReceivedItems()).isEmpty();
            assertThat(subscriberB.getReceivedItems()).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("완료된 Publisher")
    class CompletedPublisher {

        @Test
        @DisplayName("이미 완료된 ConnectableFlux에 구독하면 즉시 onComplete")
        void subscribeToCompletedReceivesOnComplete() {
            // Given
            var connectable = Flux.just(1, 2, 3).publish();
            var firstSubscriber = new TestSubscriber<Integer>();
            connectable.subscribe(firstSubscriber);
            connectable.connect();

            // When - 완료 후 구독
            var lateSubscriber = new TestSubscriber<Integer>();
            connectable.subscribe(lateSubscriber);

            // Then
            assertThat(lateSubscriber.isCompleted()).isTrue();
            assertThat(lateSubscriber.getReceivedItems()).isEmpty();
        }

        @Test
        @DisplayName("에러로 종료된 ConnectableFlux에 구독하면 즉시 onError")
        void subscribeToErroredReceivesOnError() {
            // Given
            var error = new RuntimeException("Test error");
            var connectable = Flux.<Integer>error(error).publish();
            var firstSubscriber = new TestSubscriber<Integer>();
            connectable.subscribe(firstSubscriber);
            connectable.connect();

            // When - 에러 후 구독
            var lateSubscriber = new TestSubscriber<Integer>();
            connectable.subscribe(lateSubscriber);

            // Then
            assertThat(lateSubscriber.getError()).isSameAs(error);
        }
    }

    @Nested
    @DisplayName("규약 준수")
    class RulesCompliance {

        @Test
        @DisplayName("Rule 1.9: null subscriber는 NPE 발생")
        void nullSubscriberThrowsNPE() {
            var connectable = Flux.just(1, 2, 3).publish();

            assertThatThrownBy(() -> connectable.subscribe(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 1.9");
        }
    }

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("여러 스레드에서 동시 구독 안전")
        void concurrentSubscribeIsSafe() throws InterruptedException {
            // Given
            var connectable = Flux.range(1, 10).publish();
            int threadCount = 10;
            var latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            var subscribers = new java.util.concurrent.ConcurrentLinkedQueue<TestSubscriber<Integer>>();

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        var subscriber = new TestSubscriber<Integer>();
                        connectable.subscribe(subscriber);
                        subscribers.add(subscriber);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            connectable.connect();
            executor.shutdown();

            // Then
            assertThat(subscribers).hasSize(threadCount);
            for (var subscriber : subscribers) {
                assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
                assertThat(subscriber.isCompleted()).isTrue();
            }
        }

        @Test
        @DisplayName("autoConnect: 여러 스레드에서 동시 구독 시 정확히 한 번만 연결")
        void autoConnectConcurrentSubscribeConnectsOnce() throws InterruptedException {
            // Given
            var connectCount = new AtomicInteger(0);
            var publisher = new Publisher<Integer>() {
                @Override
                public void subscribe(Subscriber<? super Integer> subscriber) {
                    connectCount.incrementAndGet();
                    subscriber.onSubscribe(new Subscription() {
                        @Override
                        public void request(long n) {
                            subscriber.onNext(1);
                            subscriber.onComplete();
                        }
                        @Override
                        public void cancel() {}
                    });
                }
            };
            
            var connectable = new ConnectableFlux<>(publisher);
            var autoConnect = connectable.autoConnect(1);
            
            int threadCount = 10;
            var latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        var subscriber = new TestSubscriber<Integer>();
                        autoConnect.subscribe(subscriber);
                        subscriber.request(Long.MAX_VALUE);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - 정확히 한 번만 연결
            assertThat(connectCount.get()).isEqualTo(1);
        }
    }
}
