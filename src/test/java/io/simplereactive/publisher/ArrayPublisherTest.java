package io.simplereactive.publisher;

import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ArrayPublisher 테스트.
 *
 * <p>Module 2의 학습 검증을 위한 테스트입니다.
 */
class ArrayPublisherTest {

    @Nested
    @DisplayName("request 동작")
    class RequestBehavior {

        @Test
        @DisplayName("request(n)만큼만 onNext 호출")
        void shouldEmitOnlyRequestedElements() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(3);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("여러 번 request 호출 시 누적")
        void shouldAccumulateMultipleRequests() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);
            subscriber.request(2);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3, 4);
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("모든 요소 발행 후 onComplete 호출")
        void shouldCompleteAfterAllElements() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("빈 배열은 즉시 onComplete")
        void shouldCompleteImmediatelyForEmptyArray() {
            // Given
            var publisher = new ArrayPublisher<Integer>();
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(1);

            // Then
            assertThat(subscriber.getReceivedItems()).isEmpty();
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("cancel 동작")
    class CancelBehavior {

        @Test
        @DisplayName("cancel 후 시그널 중지")
        void shouldStopAfterCancel() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);
            subscriber.cancel();
            subscriber.request(10);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2);
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("cancel 전 request한 만큼은 발행")
        void shouldEmitRequestedBeforeCancel() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);

            // Then
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2);

            // When
            subscriber.cancel();

            // Then - cancel 후에도 이미 받은 데이터는 유지
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2);
        }
    }

    @Nested
    @DisplayName("규약 준수")
    class SpecCompliance {

        @Test
        @DisplayName("Rule 1.9: null subscriber 시 NullPointerException")
        void shouldThrowNPEForNullSubscriber() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);

            // When & Then
            assertThatThrownBy(() -> publisher.subscribe(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 1.9");
        }

        @Test
        @DisplayName("Rule 3.9: request(0) 시 onError")
        void shouldErrorOnRequestZero() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(0);

            // Then
            assertThat(subscriber.getError())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }

        @Test
        @DisplayName("Rule 3.9: request(-1) 시 onError")
        void shouldErrorOnNegativeRequest() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(-1);

            // Then
            assertThat(subscriber.getError())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }

        @Test
        @DisplayName("Rule 1.3: onComplete는 한 번만 호출")
        void shouldCallOnCompleteOnlyOnce() {
            // Given
            var publisher = new ArrayPublisher<>(1);
            var subscriber = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(1); // onComplete 호출됨
            subscriber.request(1); // 다시 request해도 onComplete 중복 호출 안됨

            // Then
            assertThat(subscriber.isCompleted()).isTrue();
            assertThat(subscriber.getReceivedItems()).containsExactly(1);
        }
    }

    @Nested
    @DisplayName("생성자 검증")
    class ConstructorValidation {

        @Test
        @DisplayName("null 배열 시 NullPointerException")
        void shouldThrowNPEForNullArray() {
            assertThatThrownBy(() -> new ArrayPublisher<Integer>((Integer[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Array must not be null");
        }

        @Test
        @DisplayName("null 요소 포함 시 NullPointerException")
        void shouldThrowNPEForNullElement() {
            assertThatThrownBy(() -> new ArrayPublisher<>(1, null, 3))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("index 1");
        }

        @Test
        @DisplayName("원본 배열 수정이 Publisher에 영향을 주지 않음")
        void shouldBeImmutableToOriginalArrayChanges() {
            // Given
            Integer[] original = {1, 2, 3};
            var publisher = new ArrayPublisher<>(original);
            var subscriber = new TestSubscriber<Integer>();

            // When - 원본 배열 수정
            original[0] = 999;

            publisher.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then - Publisher는 원본 값 유지
            assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("Cold Publisher 특성")
    class ColdPublisherBehavior {

        @Test
        @DisplayName("각 구독자는 독립적으로 처음부터 데이터를 받음")
        void shouldStartFromBeginningForEachSubscriber() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber1 = new TestSubscriber<Integer>();
            var subscriber2 = new TestSubscriber<Integer>();

            // When
            publisher.subscribe(subscriber1);
            subscriber1.request(2);

            publisher.subscribe(subscriber2);
            subscriber2.request(3);

            // Then
            assertThat(subscriber1.getReceivedItems()).containsExactly(1, 2);
            assertThat(subscriber2.getReceivedItems()).containsExactly(1, 2, 3);
        }
    }
}
