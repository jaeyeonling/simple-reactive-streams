package io.simplereactive.operator;

import io.simplereactive.core.Flux;
import io.simplereactive.core.Publisher;
import io.simplereactive.publisher.ErrorPublisher;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ZipOperator 테스트.
 */
@DisplayName("ZipOperator")
class ZipOperatorTest {

    @Nested
    @DisplayName("두 Publisher 조합")
    class Zip2 {

        @Test
        @DisplayName("두 Publisher의 결과를 조합한다")
        void shouldCombineTwoPublishers() {
            // given
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = Flux.just(1);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> s + i
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).containsExactly("A1");
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("비동기 Publisher를 조합한다")
        void shouldCombineAsyncPublishers() throws Exception {
            // given
            Publisher<String> source1 = Flux.just("Hello");
            Publisher<String> source2 = Flux.just("World");
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (a, b) -> a + " " + b
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).containsExactly("Hello World");
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("세 Publisher 조합")
    class Zip3 {

        @Test
        @DisplayName("세 Publisher의 결과를 조합한다")
        void shouldCombineThreePublishers() {
            // given
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = Flux.just(1);
            Publisher<Boolean> source3 = Flux.just(true);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2, source3,
                    (s, i, b) -> s + i + b
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).containsExactly("A1true");
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("세 Publisher 조합 시 모든 값이 준비되면 발행한다")
        void shouldEmitWhenAllValuesReady() {
            // given
            Publisher<Integer> source1 = Flux.just(10);
            Publisher<Integer> source2 = Flux.just(20);
            Publisher<Integer> source3 = Flux.just(30);
            
            Publisher<Integer> result = ZipOperator.zip(
                    source1, source2, source3,
                    (a, b, c) -> a + b + c
            );
            
            TestSubscriber<Integer> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getItems()).containsExactly(60);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("에러 처리")
    class ErrorHandling {

        @Test
        @DisplayName("첫 번째 Publisher 에러 시 에러를 전파한다")
        void shouldPropagateFirstSourceError() {
            // given
            RuntimeException error = new RuntimeException("source1 error");
            Publisher<String> source1 = new ErrorPublisher<>(error);
            Publisher<Integer> source2 = Flux.just(1);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> s + i
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError()).isSameAs(error);
        }

        @Test
        @DisplayName("두 번째 Publisher 에러 시 에러를 전파한다")
        void shouldPropagateSecondSourceError() {
            // given
            RuntimeException error = new RuntimeException("source2 error");
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = new ErrorPublisher<>(error);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> s + i
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError()).isSameAs(error);
        }

        @Test
        @DisplayName("combinator 예외 시 에러를 전파한다")
        void shouldPropagateCombinatiorException() {
            // given
            RuntimeException error = new RuntimeException("combinator error");
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = Flux.just(1);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> { throw error; }
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError()).isSameAs(error);
        }

        @Test
        @DisplayName("combinator가 null 반환 시 에러를 발생시킨다")
        void shouldErrorOnNullCombinatorResult() {
            // given
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = Flux.just(1);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> null
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(1);

            // then
            assertThat(subscriber.getError())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Rule 2.13");
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancellation {

        @Test
        @DisplayName("cancel 시 upstream을 취소한다")
        void shouldCancelUpstreamOnCancel() {
            // given
            Publisher<String> source1 = Flux.just("A");
            Publisher<Integer> source2 = Flux.just(1);
            
            Publisher<String> result = ZipOperator.zip(
                    source1, source2,
                    (s, i) -> s + i
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.cancel();
            subscriber.request(1);

            // then - cancel 후에는 발행되지 않음
            assertThat(subscriber.getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reactive Streams 규약")
    class ReactiveStreamsSpec {

        @Test
        @DisplayName("subscriber가 null이면 NPE를 던진다 (Rule 1.9)")
        void shouldThrowOnNullSubscriber() {
            Publisher<String> result = ZipOperator.zip(
                    Flux.just("A"),
                    Flux.just(1),
                    (s, i) -> s + i
            );

            assertThatNullPointerException()
                    .isThrownBy(() -> result.subscribe(null))
                    .withMessageContaining("Rule 1.9");
        }

        @Test
        @DisplayName("n <= 0 요청 시 에러를 발생시킨다 (Rule 3.9)")
        void shouldErrorOnNonPositiveRequest() {
            // given
            Publisher<String> result = ZipOperator.zip(
                    Flux.just("A"),
                    Flux.just(1),
                    (s, i) -> s + i
            );
            
            TestSubscriber<String> subscriber = new TestSubscriber<>();

            // when
            result.subscribe(subscriber);
            subscriber.request(0);

            // then
            assertThat(subscriber.getError())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rule 3.9");
        }

        @Test
        @DisplayName("source가 null이면 NPE를 던진다")
        void shouldThrowOnNullSource() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ZipOperator.zip(
                            null,
                            Flux.just(1),
                            (String s, Integer i) -> s + i
                    ));

            assertThatNullPointerException()
                    .isThrownBy(() -> ZipOperator.zip(
                            Flux.just("A"),
                            null,
                            (String s, Integer i) -> s + i
                    ));
        }

        @Test
        @DisplayName("combinator가 null이면 NPE를 던진다")
        void shouldThrowOnNullCombinator() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ZipOperator.zip(
                            Flux.just("A"),
                            Flux.just(1),
                            null
                    ));
        }
    }
}
