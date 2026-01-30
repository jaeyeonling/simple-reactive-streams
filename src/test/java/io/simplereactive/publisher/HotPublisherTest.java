package io.simplereactive.publisher;

import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HotPublisher 테스트.
 *
 * <p>Module 7의 Hot Publisher 학습 검증을 위한 테스트입니다.
 *
 * <h2>Cold vs Hot Publisher</h2>
 * <pre>
 * Cold Publisher (ArrayPublisher)
 * - 구독할 때마다 처음부터 발행
 * - 각 구독자가 독립적인 데이터 수신
 *
 * Hot Publisher (HotPublisher)
 * - 구독 시점과 관계없이 계속 발행
 * - 늦게 구독하면 이전 데이터 놓침
 * - 모든 구독자가 같은 데이터 수신
 * </pre>
 */
class HotPublisherTest {

    @Nested
    @DisplayName("기본 발행 동작")
    class BasicEmitBehavior {

        @Test
        @DisplayName("emit된 데이터를 구독자가 수신")
        void shouldReceiveEmittedData() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.emit(1);
            hot.emit(2);
            hot.emit(3);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("complete 후 onComplete 수신")
        void shouldReceiveOnComplete() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.emit(1);
            hot.complete();

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("error 후 onError 수신")
        void shouldReceiveOnError() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);
            var error = new RuntimeException("Test error");

            // When
            hot.emit(1);
            hot.error(error);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1);
            assertThat(subscriber.getError()).isSameAs(error);
        }
    }

    @Nested
    @DisplayName("Hot Publisher 특성 - 늦은 구독")
    class LateSubscription {

        @Test
        @DisplayName("늦게 구독한 Subscriber는 이전 데이터를 받지 못함")
        void lateSubscriberMissesEarlierData() {
            // Given
            var hot = new HotPublisher<String>();
            var subscriberA = new TestSubscriber<String>();
            var subscriberB = new TestSubscriber<String>();

            // When - A 구독
            hot.subscribe(subscriberA);
            hot.emit("Hello");
            hot.emit("World");

            // B 늦게 구독
            hot.subscribe(subscriberB);
            hot.emit("Foo");
            hot.emit("Bar");
            hot.complete();

            // Then
            // A는 모든 데이터 수신
            assertThat(subscriberA.getReceivedItems())
                    .containsExactly("Hello", "World", "Foo", "Bar");
            
            // B는 구독 후 데이터만 수신 (Hello, World 놓침)
            assertThat(subscriberB.getReceivedItems())
                    .containsExactly("Foo", "Bar");
            
            // 둘 다 완료
            assertThat(subscriberA.isCompleted()).isTrue();
            assertThat(subscriberB.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("이미 완료된 Publisher에 구독하면 즉시 onComplete")
        void subscribeToCompletedPublisherReceivesOnComplete() {
            // Given
            var hot = new HotPublisher<Integer>();
            hot.emit(1);
            hot.complete();

            // When
            var lateSubscriber = new TestSubscriber<Integer>();
            hot.subscribe(lateSubscriber);

            // Then
            assertThat(lateSubscriber.getReceivedItems()).isEmpty();
            assertThat(lateSubscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("에러로 종료된 Publisher에 구독하면 즉시 onError")
        void subscribeToErroredPublisherReceivesOnError() {
            // Given
            var hot = new HotPublisher<Integer>();
            var error = new RuntimeException("Already failed");
            hot.emit(1);
            hot.error(error);

            // When
            var lateSubscriber = new TestSubscriber<Integer>();
            hot.subscribe(lateSubscriber);

            // Then
            assertThat(lateSubscriber.getReceivedItems()).isEmpty();
            assertThat(lateSubscriber.getError()).isSameAs(error);
        }
    }

    @Nested
    @DisplayName("여러 구독자 - 멀티캐스트")
    class Multicast {

        @Test
        @DisplayName("모든 구독자가 같은 데이터를 동시에 수신")
        void allSubscribersReceiveSameData() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber1 = new TestSubscriber<Integer>();
            var subscriber2 = new TestSubscriber<Integer>();
            var subscriber3 = new TestSubscriber<Integer>();

            hot.subscribe(subscriber1);
            hot.subscribe(subscriber2);
            hot.subscribe(subscriber3);

            // When
            hot.emit(1);
            hot.emit(2);
            hot.emit(3);
            hot.complete();

            // Then - 모두 동일한 데이터 수신
            assertThat(subscriber1.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriber2.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriber3.getReceivedItems()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("구독자 수 확인")
        void shouldTrackSubscriberCount() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber1 = new TestSubscriber<Integer>();
            var subscriber2 = new TestSubscriber<Integer>();

            // When & Then
            assertThat(hot.getSubscriberCount()).isEqualTo(0);

            hot.subscribe(subscriber1);
            assertThat(hot.getSubscriberCount()).isEqualTo(1);

            hot.subscribe(subscriber2);
            assertThat(hot.getSubscriberCount()).isEqualTo(2);

            // cancel로 구독 취소
            subscriber1.cancel();
            assertThat(hot.getSubscriberCount()).isEqualTo(1);

            hot.complete();
            assertThat(hot.getSubscriberCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("구독 취소")
    class Cancellation {

        @Test
        @DisplayName("cancel 후 데이터를 받지 않음")
        void shouldNotReceiveDataAfterCancel() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.emit(1);
            subscriber.cancel();
            hot.emit(2);  // 취소 후 emit
            hot.emit(3);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1);
        }

        @Test
        @DisplayName("일부 구독자만 취소해도 다른 구독자는 계속 수신")
        void otherSubscribersContinueAfterOneCancel() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber1 = new TestSubscriber<Integer>();
            var subscriber2 = new TestSubscriber<Integer>();
            hot.subscribe(subscriber1);
            hot.subscribe(subscriber2);

            // When
            hot.emit(1);
            subscriber1.cancel();  // subscriber1만 취소
            hot.emit(2);
            hot.complete();

            // Then
            assertThat(subscriber1.getReceivedItems()).containsExactly(1);
            assertThat(subscriber2.getReceivedItems()).containsExactly(1, 2);
            assertThat(subscriber2.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("종료 상태")
    class TerminalState {

        @Test
        @DisplayName("complete 후 emit은 무시됨")
        void emitAfterCompleteIsIgnored() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.emit(1);
            hot.complete();
            hot.emit(2);  // 무시되어야 함

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("complete 중복 호출 무시")
        void duplicateCompleteIsIgnored() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.complete();
            hot.complete();  // 두 번째 호출 무시

            // Then
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("error 후 complete 무시")
        void completeAfterErrorIsIgnored() {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            // When
            hot.error(new RuntimeException("First"));
            hot.complete();  // 무시되어야 함

            // Then
            assertThat(subscriber.getError()).isNotNull();
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("isCompleted()로 완료 상태 확인")
        void shouldReportCompletedState() {
            // Given
            var hot = new HotPublisher<Integer>();

            // When & Then
            assertThat(hot.isCompleted()).isFalse();
            hot.complete();
            assertThat(hot.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("규약 준수")
    class RulesCompliance {

        @Test
        @DisplayName("Rule 1.9: null subscriber는 NPE 발생")
        void nullSubscriberThrowsNPE() {
            var hot = new HotPublisher<Integer>();

            assertThatThrownBy(() -> hot.subscribe(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 1.9");
        }

        @Test
        @DisplayName("Rule 2.13: null emit은 NPE 발생")
        void nullEmitThrowsNPE() {
            var hot = new HotPublisher<Integer>();

            assertThatThrownBy(() -> hot.emit(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 2.13");
        }

        @Test
        @DisplayName("null error는 NPE 발생")
        void nullErrorThrowsNPE() {
            var hot = new HotPublisher<Integer>();

            assertThatThrownBy(() -> hot.error(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("여러 스레드에서 동시 emit 안전")
        void concurrentEmitIsSafe() throws InterruptedException {
            // Given
            var hot = new HotPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();
            hot.subscribe(subscriber);

            int threadCount = 10;
            int emitsPerThread = 100;
            var latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < emitsPerThread; i++) {
                            hot.emit(threadId * emitsPerThread + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            hot.complete();
            executor.shutdown();

            // Then
            assertThat(subscriber.getReceivedItems())
                    .hasSize(threadCount * emitsPerThread);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("emit과 subscribe 동시 호출 안전")
        void concurrentEmitAndSubscribeIsSafe() throws InterruptedException {
            // Given
            var hot = new HotPublisher<Integer>();
            int totalEmits = 1000;
            var latch = new CountDownLatch(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            var subscriber = new TestSubscriber<Integer>();

            // When - emit과 subscribe를 동시에
            executor.submit(() -> {
                try {
                    for (int i = 0; i < totalEmits; i++) {
                        hot.emit(i);
                        if (i == totalEmits / 2) {
                            Thread.yield();  // 중간에 양보
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    Thread.sleep(1);  // 약간 지연 후 구독
                    hot.subscribe(subscriber);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);
            hot.complete();
            executor.shutdown();

            // Then - 예외 없이 완료
            assertThat(subscriber.isCompleted()).isTrue();
            // 늦게 구독했으므로 일부 데이터만 받았을 수 있음
            assertThat(subscriber.getReceivedItems().size()).isLessThanOrEqualTo(totalEmits);
        }
    }

    @Nested
    @DisplayName("Cold vs Hot 비교")
    class ColdVsHotComparison {

        @Test
        @DisplayName("Cold Publisher는 구독마다 처음부터 - 비교용")
        void coldPublisherStartsFromBeginningForEachSubscriber() {
            // Given - Cold Publisher
            var cold = new ArrayPublisher<>(1, 2, 3);
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();

            // When
            cold.subscribe(subscriberA);
            subscriberA.request(Long.MAX_VALUE);

            cold.subscribe(subscriberB);
            subscriberB.request(Long.MAX_VALUE);

            // Then - 둘 다 처음부터 모든 데이터 수신
            assertThat(subscriberA.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriberB.getReceivedItems()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("Hot Publisher는 늦은 구독자가 이전 데이터 놓침 - 비교용")
        void hotPublisherLateSubscriberMissesData() {
            // Given - Hot Publisher
            var hot = new HotPublisher<Integer>();
            var subscriberA = new TestSubscriber<Integer>();
            var subscriberB = new TestSubscriber<Integer>();

            // When
            hot.subscribe(subscriberA);
            hot.emit(1);
            hot.emit(2);

            hot.subscribe(subscriberB);  // 늦은 구독
            hot.emit(3);
            hot.complete();

            // Then
            // A는 모든 데이터 수신
            assertThat(subscriberA.getReceivedItems()).containsExactly(1, 2, 3);
            // B는 구독 후 데이터만 수신
            assertThat(subscriberB.getReceivedItems()).containsExactly(3);
        }
    }
}
