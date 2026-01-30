package io.simplereactive.operator;

import io.simplereactive.core.Flux;
import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.publisher.ErrorPublisher;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("에러 처리 테스트")
class ErrorHandlingTest {

    @Nested
    @DisplayName("ErrorPublisher")
    class ErrorPublisherTests {

        @Test
        @DisplayName("구독 시 즉시 에러 발행")
        void publishesErrorImmediately() {
            var error = new RuntimeException("Test error");
            var subscriber = new TestSubscriber<String>();

            new ErrorPublisher<String>(error).subscribe(subscriber);
            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isSameAs(error);
            assertThat(subscriber.isCompleted()).isFalse();
            assertThat(subscriber.getItems()).isEmpty();
        }

        @Test
        @DisplayName("null 에러로 생성 시 NullPointerException")
        void throwsOnNullError() {
            assertThatThrownBy(() -> new ErrorPublisher<String>(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null subscriber 시 NullPointerException (Rule 1.9)")
        void throwsOnNullSubscriber() {
            var error = new RuntimeException("Test error");
            assertThatThrownBy(() -> new ErrorPublisher<String>(error).subscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Flux.error() 팩토리 메서드")
        void fluxErrorFactory() {
            var error = new RuntimeException("Test error");
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(error).subscribe(subscriber);
            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isSameAs(error);
        }
    }

    @Nested
    @DisplayName("OnErrorResumeOperator")
    class OnErrorResumeTests {

        @Test
        @DisplayName("에러 발생 시 fallback Publisher로 전환")
        void resumesWithFallbackOnError() {
            var error = new RuntimeException("Oops!");
            var subscriber = new TestSubscriber<Integer>();

            // 1, 2 발행 후 에러
            Flux.<Integer>from(sub -> {
                sub.onSubscribe(new io.simplereactive.test.ManualSubscription());
                sub.onNext(1);
                sub.onNext(2);
                sub.onError(error);
            })
            .onErrorResume(e -> new ArrayPublisher<>(3, 4))
            .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(1, 2, 3, 4);
            assertThat(subscriber.isCompleted()).isTrue();
            assertThat(subscriber.hasError()).isFalse();
        }

        @Test
        @DisplayName("에러 없이 정상 완료 시 fallback 호출 안 함")
        void doesNotResumeOnNormalCompletion() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.just(1, 2, 3)
                    .onErrorResume(e -> new ArrayPublisher<>(-1))
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("fallback 함수에서 예외 발생 시 에러 전파")
        void propagatesErrorIfFallbackThrows() {
            var originalError = new RuntimeException("Original");
            var fallbackError = new RuntimeException("Fallback failed");
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(originalError)
                    .onErrorResume(e -> { throw fallbackError; })
                    .subscribe(subscriber);

            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isSameAs(fallbackError);
            assertThat(subscriber.getError().getSuppressed()).contains(originalError);
        }

        @Test
        @DisplayName("fallback이 null 반환 시 NullPointerException")
        void errorsIfFallbackReturnsNull() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new RuntimeException("Test"))
                    .onErrorResume(e -> null)
                    .subscribe(subscriber);

            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("에러 타입에 따라 다른 fallback 적용")
        void differentFallbackBasedOnErrorType() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new IllegalArgumentException("Bad arg"))
                    .onErrorResume(e -> {
                        if (e instanceof IllegalArgumentException) {
                            return new ArrayPublisher<>(-1);
                        }
                        return new ArrayPublisher<>(-2);
                    })
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(-1);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("OnErrorReturnOperator")
    class OnErrorReturnTests {

        @Test
        @DisplayName("에러 발생 시 기본값 반환 후 완료")
        void returnsDefaultValueOnError() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>from(sub -> {
                sub.onSubscribe(new io.simplereactive.test.ManualSubscription());
                sub.onNext(1);
                sub.onNext(2);
                sub.onError(new RuntimeException("Oops!"));
            })
            .onErrorReturn(e -> -1)
            .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(1, 2, -1);
            assertThat(subscriber.isCompleted()).isTrue();
            assertThat(subscriber.hasError()).isFalse();
        }

        @Test
        @DisplayName("고정 기본값 반환")
        void returnsFixedDefaultValue() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new RuntimeException("Test"))
                    .onErrorReturn(-999)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(-999);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("에러 없이 정상 완료 시 기본값 사용 안 함")
        void doesNotReturnDefaultOnNormalCompletion() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.just(1, 2, 3)
                    .onErrorReturn(-1)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("fallback 함수에서 예외 발생 시 에러 전파")
        void propagatesErrorIfFallbackThrows() {
            var originalError = new RuntimeException("Original");
            var fallbackError = new RuntimeException("Fallback failed");
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(originalError)
                    .onErrorReturn(e -> { throw fallbackError; })
                    .subscribe(subscriber);

            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isSameAs(fallbackError);
        }

        @Test
        @DisplayName("fallback이 null 반환 시 NullPointerException (Rule 2.13)")
        void errorsIfFallbackReturnsNull() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new RuntimeException("Test"))
                    .onErrorReturn(e -> null)
                    .subscribe(subscriber);

            subscriber.request(1);

            assertThat(subscriber.hasError()).isTrue();
            assertThat(subscriber.getError()).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("에러 처리 체이닝")
    class ErrorHandlingChainTests {

        @Test
        @DisplayName("map에서 에러 발생 시 onErrorReturn으로 복구")
        void mapErrorRecoveredByOnErrorReturn() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.just(1, 2, 3, 4, 5)
                    .map(x -> {
                        if (x == 3) throw new RuntimeException("Error at 3");
                        return x * 2;
                    })
                    .onErrorReturn(-1)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(2, 4, -1);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("onErrorResume 후 다시 map 적용")
        void onErrorResumeThenMap() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new RuntimeException("Test"))
                    .onErrorResume(e -> Flux.just(1, 2, 3))
                    .map(x -> x * 10)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(10, 20, 30);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("여러 onErrorReturn 체이닝 - 첫 번째만 적용")
        void multipleOnErrorReturnUsesFirst() {
            var subscriber = new TestSubscriber<Integer>();

            Flux.<Integer>error(new RuntimeException("Test"))
                    .onErrorReturn(-1)
                    .onErrorReturn(-2)  // 이미 복구되어 호출 안 됨
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getItems()).containsExactly(-1);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }
}
