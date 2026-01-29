package io.simplereactive.subscriber;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BufferedSubscriber 테스트.
 *
 * <p>Module 3의 Backpressure 학습 검증을 위한 테스트입니다.
 */
class BufferedSubscriberTest {

    @Nested
    @DisplayName("기본 동작")
    class BasicBehavior {

        @Test
        @DisplayName("버퍼를 통해 데이터가 전달됨")
        void shouldPassDataThroughBuffer() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);
            var publisher = new ArrayPublisher<>(1, 2, 3);

            // When
            publisher.subscribe(buffered);
            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(downstream.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("downstream의 demand에 따라 전달")
        void shouldRespectDownstreamDemand() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);

            // When
            publisher.subscribe(buffered);
            downstream.request(2);

            // Then
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2);
            assertThat(downstream.isCompleted()).isFalse();

            // When - 추가 요청
            downstream.request(3);

            // Then
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2, 3, 4, 5);
            assertThat(downstream.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("빈 Publisher는 즉시 완료")
        void shouldCompleteImmediatelyForEmptyPublisher() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);
            var publisher = new ArrayPublisher<Integer>();

            // When
            publisher.subscribe(buffered);
            downstream.request(1);

            // Then
            assertThat(downstream.getReceivedItems()).isEmpty();
            assertThat(downstream.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("DROP_OLDEST 전략")
    class DropOldestStrategy {

        @Test
        @DisplayName("버퍼가 가득 차면 오래된 데이터를 버림")
        void shouldDropOldestWhenBufferFull() {
            // Given - 버퍼 크기 3
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_OLDEST);

            // 느린 Publisher 시뮬레이션 (직접 시그널 전송)
            var subscription = new ManualSubscription();
            buffered.onSubscribe(subscription);

            // When - 버퍼 크기(3)를 초과하여 데이터 전송
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4); // overflow - 1 dropped
            buffered.onNext(5); // overflow - 2 dropped
            buffered.onComplete();

            // request하면 남은 데이터 전달
            downstream.request(Long.MAX_VALUE);

            // Then - 최신 3개만 남음
            assertThat(downstream.getReceivedItems()).containsExactly(3, 4, 5);
        }
    }

    @Nested
    @DisplayName("DROP_LATEST 전략")
    class DropLatestStrategy {

        @Test
        @DisplayName("버퍼가 가득 차면 새로운 데이터를 버림")
        void shouldDropLatestWhenBufferFull() {
            // Given - 버퍼 크기 3
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_LATEST);

            var subscription = new ManualSubscription();
            buffered.onSubscribe(subscription);

            // When - 버퍼 크기(3)를 초과하여 데이터 전송
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4); // dropped
            buffered.onNext(5); // dropped
            buffered.onComplete();

            downstream.request(Long.MAX_VALUE);

            // Then - 처음 3개만 남음
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("ERROR 전략")
    class ErrorStrategy {

        @Test
        @DisplayName("버퍼가 가득 차면 에러 발생")
        void shouldErrorWhenBufferFull() {
            // Given - 버퍼 크기 3
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.ERROR);

            var subscription = new ManualSubscription();
            buffered.onSubscribe(subscription);

            // When - 버퍼 크기(3)를 초과
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4); // overflow!

            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getError())
                    .isInstanceOf(BufferOverflowException.class);
            assertThat(subscription.isCancelled()).isTrue();
        }

        @Test
        @DisplayName("BufferOverflowException에 버퍼 크기 정보 포함")
        void shouldIncludeBufferSizeInException() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 5, OverflowStrategy.ERROR);

            var subscription = new ManualSubscription();
            buffered.onSubscribe(subscription);

            // When - 오버플로우 발생
            for (int i = 0; i < 6; i++) {
                buffered.onNext(i);
            }
            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getError())
                    .isInstanceOf(BufferOverflowException.class)
                    .satisfies(e -> {
                        var boe = (BufferOverflowException) e;
                        assertThat(boe.getBufferSize()).isEqualTo(5);
                    });
        }
    }

    @Nested
    @DisplayName("생성자 검증")
    class ConstructorValidation {

        @Test
        @DisplayName("downstream이 null이면 NPE")
        void shouldThrowNPEForNullDownstream() {
            assertThatThrownBy(() -> new BufferedSubscriber<>(null, 10, OverflowStrategy.DROP_LATEST))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Downstream");
        }

        @Test
        @DisplayName("strategy가 null이면 NPE")
        void shouldThrowNPEForNullStrategy() {
            var downstream = new TestSubscriber<Integer>();
            assertThatThrownBy(() -> new BufferedSubscriber<>(downstream, 10, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Strategy");
        }

        @Test
        @DisplayName("bufferSize가 0이면 IAE")
        void shouldThrowIAEForZeroBufferSize() {
            var downstream = new TestSubscriber<Integer>();
            assertThatThrownBy(() -> new BufferedSubscriber<>(downstream, 0, OverflowStrategy.DROP_LATEST))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("bufferSize가 음수면 IAE")
        void shouldThrowIAEForNegativeBufferSize() {
            var downstream = new TestSubscriber<Integer>();
            assertThatThrownBy(() -> new BufferedSubscriber<>(downstream, -1, OverflowStrategy.DROP_LATEST))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("cancel 동작")
    class CancelBehavior {

        @Test
        @DisplayName("cancel 시 upstream도 cancel됨")
        void shouldCancelUpstreamOnCancel() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);

            var upstreamSubscription = new ManualSubscription();
            buffered.onSubscribe(upstreamSubscription);

            // When
            downstream.cancel();

            // Then
            assertThat(upstreamSubscription.isCancelled()).isTrue();
        }
    }

    @Nested
    @DisplayName("prefetch 동작")
    class PrefetchBehavior {

        @Test
        @DisplayName("구독 시 버퍼 크기만큼 미리 요청")
        void shouldPrefetchBufferSizeOnSubscribe() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 5, OverflowStrategy.DROP_LATEST);

            var upstreamSubscription = new ManualSubscription();

            // When
            buffered.onSubscribe(upstreamSubscription);

            // Then - 버퍼 크기(5)만큼 미리 요청됨
            assertThat(upstreamSubscription.getRequestedCount()).isEqualTo(5);
        }
    }

    /**
     * 테스트용 수동 Subscription.
     */
    static class ManualSubscription implements Subscription {
        private final AtomicInteger requested = new AtomicInteger(0);
        private volatile boolean cancelled = false;

        @Override
        public void request(long n) {
            if (n > 0) {
                requested.addAndGet((int) Math.min(n, Integer.MAX_VALUE));
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        int getRequestedCount() {
            return requested.get();
        }

        boolean isCancelled() {
            return cancelled;
        }
    }
}
