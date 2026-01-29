package io.simplereactive.publisher;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(3);

            // Then
            assertThat(subscriber.received).containsExactly(1, 2, 3);
            assertThat(subscriber.completed).isFalse();
        }

        @Test
        @DisplayName("여러 번 request 호출 시 누적")
        void shouldAccumulateMultipleRequests() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);
            subscriber.request(2);

            // Then
            assertThat(subscriber.received).containsExactly(1, 2, 3, 4);
            assertThat(subscriber.completed).isFalse();
        }

        @Test
        @DisplayName("모든 요소 발행 후 onComplete 호출")
        void shouldCompleteAfterAllElements() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.received).containsExactly(1, 2, 3);
            assertThat(subscriber.completed).isTrue();
        }

        @Test
        @DisplayName("빈 배열은 즉시 onComplete")
        void shouldCompleteImmediatelyForEmptyArray() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>();
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(1);

            // Then
            assertThat(subscriber.received).isEmpty();
            assertThat(subscriber.completed).isTrue();
        }
    }

    @Nested
    @DisplayName("cancel 동작")
    class CancelBehavior {

        @Test
        @DisplayName("cancel 후 시그널 중지")
        void shouldStopAfterCancel() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);
            subscriber.cancel();
            subscriber.request(10);

            // Then
            assertThat(subscriber.received).containsExactly(1, 2);
            assertThat(subscriber.completed).isFalse();
        }

        @Test
        @DisplayName("cancel 전 request한 만큼은 발행")
        void shouldEmitRequestedBeforeCancel() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(2);

            // Then
            assertThat(subscriber.received).containsExactly(1, 2);

            // When
            subscriber.cancel();

            // Then - cancel 후에도 이미 받은 데이터는 유지
            assertThat(subscriber.received).containsExactly(1, 2);
        }
    }

    @Nested
    @DisplayName("규약 준수")
    class SpecCompliance {

        @Test
        @DisplayName("Rule 1.9: null subscriber 시 NullPointerException")
        void shouldThrowNPEForNullSubscriber() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);

            // When & Then
            assertThatThrownBy(() -> publisher.subscribe(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 1.9");
        }

        @Test
        @DisplayName("Rule 3.9: request(0) 시 onError")
        void shouldErrorOnRequestZero() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(0);

            // Then
            assertThat(subscriber.error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }

        @Test
        @DisplayName("Rule 3.9: request(-1) 시 onError")
        void shouldErrorOnNegativeRequest() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber);
            subscriber.request(-1);

            // Then
            assertThat(subscriber.error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }
    }

    @Nested
    @DisplayName("Cold Publisher 특성")
    class ColdPublisherBehavior {

        @Test
        @DisplayName("각 구독자는 독립적으로 처음부터 데이터를 받음")
        void shouldStartFromBeginningForEachSubscriber() {
            // Given
            ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
            TestSubscriber<Integer> subscriber1 = new TestSubscriber<>();
            TestSubscriber<Integer> subscriber2 = new TestSubscriber<>();

            // When
            publisher.subscribe(subscriber1);
            subscriber1.request(2);

            publisher.subscribe(subscriber2);
            subscriber2.request(3);

            // Then
            assertThat(subscriber1.received).containsExactly(1, 2);
            assertThat(subscriber2.received).containsExactly(1, 2, 3);
        }
    }

    /**
     * 테스트용 Subscriber.
     *
     * @param <T> 요소 타입
     */
    static class TestSubscriber<T> implements Subscriber<T> {
        final List<T> received = new ArrayList<>();
        Subscription subscription;
        Throwable error;
        boolean completed;

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
        }

        @Override
        public void onNext(T item) {
            received.add(item);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onComplete() {
            this.completed = true;
        }

        void request(long n) {
            subscription.request(n);
        }

        void cancel() {
            subscription.cancel();
        }
    }
}
