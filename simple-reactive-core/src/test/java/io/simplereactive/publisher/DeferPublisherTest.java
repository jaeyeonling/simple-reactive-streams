package io.simplereactive.publisher;

import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * DeferPublisher 테스트.
 */
@DisplayName("DeferPublisher")
class DeferPublisherTest {

    @Nested
    @DisplayName("기본 동작")
    class BasicBehavior {

        @Test
        @DisplayName("Callable 결과를 발행한다")
        void shouldEmitCallableResult() {
            // given
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> "hello");
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).containsExactly("hello");
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("request 전까지 Callable을 실행하지 않는다")
        void shouldNotExecuteUntilRequested() {
            // given
            AtomicInteger callCount = new AtomicInteger(0);
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> {
                callCount.incrementAndGet();
                return "result";
            });
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);

            // then
            assertThat(callCount.get()).isZero();

            // when
            subscriber.request(1);

            // then
            assertThat(callCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Callable은 한 번만 실행된다")
        void shouldExecuteOnlyOnce() {
            // given
            AtomicInteger callCount = new AtomicInteger(0);
            DeferPublisher<Integer> publisher = new DeferPublisher<>(() -> {
                return callCount.incrementAndGet();
            });
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(10); // 여러 번 요청

            // then
            assertThat(callCount.get()).isEqualTo(1);
            assertThat(subscriber.getItems()).containsExactly(1);
        }
    }

    @Nested
    @DisplayName("에러 처리")
    class ErrorHandling {

        @Test
        @DisplayName("Callable 예외를 onError로 전달한다")
        void shouldPropagateException() {
            // given
            RuntimeException error = new RuntimeException("test error");
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> {
                throw error;
            });
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError()).isSameAs(error);
            assertThat(subscriber.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Callable이 null을 반환하면 onError를 호출한다")
        void shouldErrorOnNullResult() {
            // given
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> null);
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 2.13");
        }

        @Test
        @DisplayName("n <= 0 요청 시 onError를 호출한다 (Rule 3.9)")
        void shouldErrorOnNonPositiveRequest() {
            // given
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> "value");
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.request(0);

            // then
            assertThat(subscriber.getError())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancellation {

        @Test
        @DisplayName("cancel 후에는 발행하지 않는다")
        void shouldNotEmitAfterCancel() {
            // given
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> "value");
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            publisher.subscribe(subscriber);
            subscriber.cancel();
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).isEmpty();
            assertThat(subscriber.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Reactive Streams 규약")
    class ReactiveStreamsSpec {

        @Test
        @DisplayName("subscriber가 null이면 NPE를 던진다 (Rule 1.9)")
        void shouldThrowOnNullSubscriber() {
            DeferPublisher<String> publisher = new DeferPublisher<>(() -> "value");

            assertThatNullPointerException()
                    .isThrownBy(() -> publisher.subscribe(null))
                    .withMessageContaining("Rule 1.9");
        }

        @Test
        @DisplayName("callable이 null이면 NPE를 던진다")
        void shouldThrowOnNullCallable() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DeferPublisher<>(null));
        }
    }
}
