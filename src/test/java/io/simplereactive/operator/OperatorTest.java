package io.simplereactive.operator;

import io.simplereactive.core.Flux;
import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Operator 테스트.
 *
 * <p>Module 4의 학습 검증을 위한 테스트입니다.
 */
@DisplayName("Operator 테스트")
class OperatorTest {

    // =========================================================================
    // MapOperator 테스트
    // =========================================================================

    @Nested
    @DisplayName("MapOperator")
    class MapOperatorTest {

        @Test
        @DisplayName("각 요소를 변환한다")
        void shouldMapEachElement() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var mapped = new MapOperator<>(publisher, x -> x * 2);
            var subscriber = new TestSubscriber<Integer>();

            // When
            mapped.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2, 4, 6);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("타입을 변환할 수 있다")
        void shouldTransformType() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var mapped = new MapOperator<>(publisher, x -> "num:" + x);
            var subscriber = new TestSubscriber<String>();

            // When
            mapped.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly("num:1", "num:2", "num:3");
        }

        @Test
        @DisplayName("mapper에서 예외 발생 시 onError")
        void shouldErrorWhenMapperThrows() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 0, 4);
            var mapped = new MapOperator<>(publisher, x -> 10 / x);  // 0으로 나누기!
            var subscriber = new TestSubscriber<Integer>();

            // When
            mapped.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(10, 5);  // 1, 2까지 처리
            assertThat(subscriber.getError())
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("mapper가 null 반환 시 onError")
        void shouldErrorWhenMapperReturnsNull() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var mapped = new MapOperator<>(publisher, x -> x == 2 ? null : x);
            var subscriber = new TestSubscriber<Integer>();

            // When
            mapped.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1);
            assertThat(subscriber.getError())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("backpressure를 지원한다")
        void shouldSupportBackpressure() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var mapped = new MapOperator<>(publisher, x -> x * 10);
            var subscriber = new TestSubscriber<Integer>();

            // When
            mapped.subscribe(subscriber);
            subscriber.request(2);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(10, 20);
            assertThat(subscriber.isCompleted()).isFalse();

            // When - 나머지 요청
            subscriber.request(3);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(10, 20, 30, 40, 50);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    // =========================================================================
    // FilterOperator 테스트
    // =========================================================================

    @Nested
    @DisplayName("FilterOperator")
    class FilterOperatorTest {

        @Test
        @DisplayName("조건에 맞는 요소만 통과시킨다")
        void shouldFilterElements() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5, 6);
            var filtered = new FilterOperator<>(publisher, x -> x % 2 == 0);
            var subscriber = new TestSubscriber<Integer>();

            // When
            filtered.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2, 4, 6);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("모든 요소가 필터링되면 빈 결과")
        void shouldReturnEmptyWhenAllFiltered() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var filtered = new FilterOperator<>(publisher, x -> x > 10);
            var subscriber = new TestSubscriber<Integer>();

            // When
            filtered.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems()).isEmpty();
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("predicate에서 예외 발생 시 onError")
        void shouldErrorWhenPredicateThrows() {
            // Given: predicate에서 예외 발생
            var publisher = new ArrayPublisher<>(1, 2, 3, 4);
            var filtered = new FilterOperator<>(publisher, x -> {
                if (x == 3) {
                    throw new RuntimeException("Test exception");
                }
                return x % 2 == 0;
            });
            var subscriber = new TestSubscriber<Integer>();

            // When
            filtered.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2);  // 1은 필터링, 2는 통과, 3에서 예외
            assertThat(subscriber.getError())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Test exception");
        }

        @Test
        @DisplayName("필터링된 요소만큼 추가 request를 보낸다")
        void shouldRequestMoreWhenFiltered() {
            // Given: 1~10 중 짝수만 필터
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            var filtered = new FilterOperator<>(publisher, x -> x % 2 == 0);
            var subscriber = new TestSubscriber<Integer>();

            // When: 3개 요청
            filtered.subscribe(subscriber);
            subscriber.request(3);

            // Then: 짝수 3개 받음 (2, 4, 6)
            // 내부적으로는 홀수 필터링될 때마다 추가 request
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2, 4, 6);
        }
    }

    // =========================================================================
    // TakeOperator 테스트
    // =========================================================================

    @Nested
    @DisplayName("TakeOperator")
    class TakeOperatorTest {

        @Test
        @DisplayName("처음 n개만 가져온다")
        void shouldTakeFirstN() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var taken = new TakeOperator<>(publisher, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            taken.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("소스가 limit보다 적으면 모두 가져온다")
        void shouldTakeAllWhenSourceIsSmaller() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2);
            var taken = new TakeOperator<>(publisher, 5);
            var subscriber = new TestSubscriber<Integer>();

            // When
            taken.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("limit 도달 후 upstream을 cancel한다")
        void shouldCancelUpstreamAfterLimit() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var taken = new TakeOperator<>(publisher, 2);
            var subscriber = new TestSubscriber<Integer>();

            // When
            taken.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2);
            assertThat(subscriber.isCompleted()).isTrue();
            // upstream cancel은 내부에서 호출됨
        }

        @Test
        @DisplayName("limit이 0 이하면 예외")
        void shouldThrowWhenLimitIsNotPositive() {
            var publisher = new ArrayPublisher<>(1, 2, 3);

            assertThatThrownBy(() -> new TakeOperator<>(publisher, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> new TakeOperator<>(publisher, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("backpressure를 지원한다")
        void shouldSupportBackpressure() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
            var taken = new TakeOperator<>(publisher, 4);
            var subscriber = new TestSubscriber<Integer>();

            // When
            taken.subscribe(subscriber);
            subscriber.request(2);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2);
            assertThat(subscriber.isCompleted()).isFalse();

            // When - 나머지 요청
            subscriber.request(10);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2, 3, 4);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }

    // =========================================================================
    // Flux (체이닝) 테스트
    // =========================================================================

    @Nested
    @DisplayName("Flux 체이닝")
    class FluxChainingTest {

        @Test
        @DisplayName("map -> filter -> take 체이닝")
        void shouldChainOperators() {
            // Given
            var subscriber = new TestSubscriber<Integer>();

            // When
            Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                    .map(x -> x * 2)        // [2,4,6,8,10,12,14,16,18,20]
                    .filter(x -> x > 10)    // [12,14,16,18,20]
                    .take(3)                // [12,14,16]
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(12, 14, 16);
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("Flux.range로 범위 생성")
        void shouldCreateRange() {
            // Given
            var subscriber = new TestSubscriber<Integer>();

            // When
            Flux.range(1, 5)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("Flux.empty는 즉시 완료")
        void shouldCompleteImmediatelyForEmpty() {
            // Given
            var subscriber = new TestSubscriber<Integer>();

            // When
            Flux.<Integer>empty()
                    .subscribe(subscriber);

            subscriber.request(1);

            // Then
            assertThat(subscriber.getReceivedItems()).isEmpty();
            assertThat(subscriber.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("복잡한 체이닝")
        void shouldHandleComplexChaining() {
            // Given
            var subscriber = new TestSubscriber<String>();

            // When
            Flux.range(1, 100)
                    .filter(x -> x % 3 == 0)    // 3의 배수
                    .map(x -> x * x)            // 제곱
                    .filter(x -> x > 100)       // 100 초과
                    .take(5)                    // 처음 5개
                    .map(x -> "result:" + x)    // 문자열 변환
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            // Then
            // 3의 배수: 3,6,9,12,15,18,...
            // 제곱: 9,36,81,144,225,324,...
            // 100 초과: 144,225,324,441,576,...
            // 처음 5개: 144,225,324,441,576
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(
                            "result:144",
                            "result:225",
                            "result:324",
                            "result:441",
                            "result:576"
                    );
        }

        @Test
        @DisplayName("기존 Publisher를 Flux로 변환")
        void shouldWrapExistingPublisher() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3);
            var subscriber = new TestSubscriber<Integer>();

            // When
            Flux.from(publisher)
                    .map(x -> x + 10)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            // Then
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(11, 12, 13);
        }
    }

    // =========================================================================
    // Operator 개념 학습 테스트
    // =========================================================================

    @Nested
    @DisplayName("Operator 개념 학습")
    class OperatorConceptTest {

        /**
         * <pre>
         * Operator는 Publisher를 입력받아 새로운 Publisher를 반환합니다.
         * 이를 통해 데이터 변환 파이프라인을 구성할 수 있습니다.
         *
         *    Source         Operator        Operator        Terminal
         *    Publisher ────────────────────────────────────> Subscriber
         *       │              │              │                 │
         *       │   map(x*2)   │  filter(>5)  │    take(2)     │
         *       │              │              │                 │
         *      [1,2,3]      [2,4,6]         [6]              [6]
         * </pre>
         */
        @Test
        @DisplayName("Operator는 Publisher이자 Subscriber이다")
        void operatorIsBothPublisherAndSubscriber() {
            // MapOperator는 Publisher<R>을 구현
            var mapOp = new MapOperator<>(
                    new ArrayPublisher<>(1, 2, 3),
                    x -> x * 2
            );

            // Publisher로서 subscribe 가능
            var subscriber = new TestSubscriber<Integer>();
            mapOp.subscribe(subscriber);
            subscriber.request(Long.MAX_VALUE);

            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2, 4, 6);
        }

        /**
         * <pre>
         * FilterOperator의 특별한 점:
         * 필터링된 요소는 downstream에 전달되지 않으므로,
         * downstream이 요청한 개수를 충족하기 위해 추가 request를 보냅니다.
         *
         * downstream.request(3) → 3개 원함
         * 필터: x % 2 == 0 (짝수만)
         *
         * onNext(1) → 필터링 → request(1) 추가
         * onNext(2) → 통과 → downstream.onNext(2)
         * onNext(3) → 필터링 → request(1) 추가
         * onNext(4) → 통과 → downstream.onNext(4)
         * onNext(5) → 필터링 → request(1) 추가
         * onNext(6) → 통과 → downstream.onNext(6)
         *
         * 결과: downstream은 요청한 3개(2,4,6)를 받음
         * </pre>
         */
        @Test
        @DisplayName("FilterOperator는 필터링된 만큼 추가 request를 보낸다")
        void filterOperatorRequestsMoreWhenFiltering() {
            // Given: 1~6 중 짝수만
            var subscriber = new TestSubscriber<Integer>();

            Flux.just(1, 2, 3, 4, 5, 6)
                    .filter(x -> x % 2 == 0)
                    .subscribe(subscriber);

            // When: 2개 요청
            subscriber.request(2);

            // Then: 짝수 2개(2, 4) 받음
            // 내부적으로 1, 3이 필터링될 때마다 추가 request 발생
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(2, 4);
        }

        /**
         * <pre>
         * TakeOperator의 특별한 점:
         * limit에 도달하면 upstream을 cancel하고 완료합니다.
         * 이를 통해 무한 스트림도 안전하게 처리할 수 있습니다.
         * </pre>
         */
        @Test
        @DisplayName("TakeOperator는 limit 도달 시 upstream을 cancel한다")
        void takeOperatorCancelsUpstreamAtLimit() {
            // Given: range(1, 1000000) - 매우 큰 범위
            var subscriber = new TestSubscriber<Integer>();

            // When: take(3)으로 3개만
            Flux.range(1, 1000000)
                    .take(3)
                    .subscribe(subscriber);

            subscriber.request(Long.MAX_VALUE);

            // Then: 3개만 받고 완료 (나머지는 생성되지 않음)
            assertThat(subscriber.getReceivedItems())
                    .containsExactly(1, 2, 3);
            assertThat(subscriber.isCompleted()).isTrue();
        }
    }
}
