package io.simplereactive.subscriber;

import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.test.ManualSubscription;
import io.simplereactive.test.TestSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BufferedSubscriber의 핵심 동작을 학습하기 위한 테스트.
 *
 * <p>이 테스트는 BufferedSubscriber의 세 가지 핵심 메커니즘을 이해하기 위해 작성되었습니다:
 * <ul>
 *   <li><b>Prefetch</b>: 구독 시 버퍼 크기만큼 미리 요청</li>
 *   <li><b>Replenish</b>: 소비한 만큼 다시 요청</li>
 *   <li><b>Overflow</b>: 버퍼 초과 시 전략에 따른 처리</li>
 * </ul>
 *
 * <h2>핵심 개념</h2>
 * <pre>
 * BufferedSubscriber는 "속도 조절"이 아닌 "속도 차이 완충"을 담당합니다.
 * 실제 속도 조절은 Downstream의 request(n)이 결정합니다.
 *
 *     Upstream ──────> [Buffer] ──────> Downstream
 *                         │
 *           ┌─────────────┼─────────────┐
 *           │             │             │
 *       Prefetch      Buffering     Replenish
 *     (미리 요청)    (임시 저장)   (다시 요청)
 * </pre>
 *
 * @see BufferedSubscriber
 * @see io.simplereactive.subscription.BufferedSubscription
 */
@DisplayName("BufferedSubscriber 핵심 동작 학습")
class BufferedSubscriberFlowTest {

    // =========================================================================
    // 1. PREFETCH (미리 당겨오기)
    // =========================================================================

    @Nested
    @DisplayName("1. Prefetch 동작")
    class PrefetchBehavior {

        /**
         * <pre>
         * Prefetch란?
         * ─────────────
         * 구독 시작 시 버퍼 크기만큼 미리 요청하여 버퍼를 채워둡니다.
         *
         * 왜 필요한가?
         * ─────────────
         * Prefetch 없이:
         *   downstream.request(1) → upstream.request(1) → [대기] → 데이터 도착
         *   → 매번 왕복 대기 시간 발생
         *
         * Prefetch 있으면:
         *   구독 시 → upstream.request(bufferSize) → 버퍼에 미리 채움
         *   downstream.request(1) → 버퍼에서 즉시 제공 (대기 없음!)
         * </pre>
         */
        @Test
        @DisplayName("구독 시 버퍼 크기만큼 upstream에 미리 요청한다")
        void shouldPrefetchBufferSizeOnSubscribe() {
            // Given: 버퍼 크기 5인 BufferedSubscriber
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 5, OverflowStrategy.DROP_LATEST);

            var upstreamSubscription = new ManualSubscription();

            // When: 구독 시작
            buffered.onSubscribe(upstreamSubscription);

            // Then: 버퍼 크기(5)만큼 미리 요청됨
            assertThat(upstreamSubscription.getRequestedCount())
                    .as("Prefetch: 버퍼 크기만큼 미리 요청해야 함")
                    .isEqualTo(5);

            assertThat(upstreamSubscription.getRequestHistory())
                    .as("request 호출 히스토리")
                    .containsExactly(5L);
        }

        /**
         * <pre>
         * Prefetch 시점 확인
         * ───────────────────
         * Prefetch는 onSubscribe 내에서 즉시 발생합니다.
         * downstream이 request를 호출하기 전에 이미 데이터를 당겨옵니다.
         * </pre>
         */
        @Test
        @DisplayName("Prefetch는 downstream의 request 전에 발생한다")
        void prefetchHappensBeforeDownstreamRequest() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_LATEST);

            var upstreamSubscription = new ManualSubscription();

            // When: 구독만 하고 downstream은 아직 request 안 함
            buffered.onSubscribe(upstreamSubscription);

            // Then: 이미 prefetch가 발생함
            assertThat(upstreamSubscription.getRequestedCount())
                    .as("downstream.request() 전에 이미 prefetch 발생")
                    .isEqualTo(3);

            // downstream은 아직 request 안 했음
            assertThat(downstream.getSubscription()).isNotNull();
        }

        /**
         * <pre>
         * 버퍼 크기에 따른 Prefetch
         * ───────────────────────────
         * 버퍼 크기가 다르면 prefetch 양도 다릅니다.
         * </pre>
         */
        @Test
        @DisplayName("버퍼 크기에 따라 prefetch 양이 달라진다")
        void prefetchAmountDependsOnBufferSize() {
            // Given: 다양한 버퍼 크기
            int[] bufferSizes = {1, 10, 100};

            for (int bufferSize : bufferSizes) {
                var downstream = new TestSubscriber<Integer>();
                var buffered = new BufferedSubscriber<>(downstream, bufferSize, OverflowStrategy.DROP_LATEST);
                var upstreamSubscription = new ManualSubscription();

                // When
                buffered.onSubscribe(upstreamSubscription);

                // Then
                assertThat(upstreamSubscription.getRequestedCount())
                        .as("버퍼 크기 %d일 때 prefetch", bufferSize)
                        .isEqualTo(bufferSize);
            }
        }
    }

    // =========================================================================
    // 2. REPLENISH (다시 채우기)
    // =========================================================================

    @Nested
    @DisplayName("2. Replenish 동작")
    class ReplenishBehavior {

        /**
         * <pre>
         * Replenish란?
         * ─────────────
         * downstream에 데이터를 전달한 후, 전달한 만큼 upstream에 다시 요청합니다.
         *
         * 흐름:
         *   1. downstream.request(3)
         *   2. buffer에서 3개 꺼내서 downstream에 전달
         *   3. upstream.request(3)  ← Replenish!
         *
         * 왜 필요한가?
         * ─────────────
         * 버퍼가 비어서 대기하는 것을 방지합니다.
         * 항상 버퍼를 채워두어 downstream이 요청하면 즉시 응답할 수 있습니다.
         * </pre>
         */
        @Test
        @DisplayName("데이터를 전달한 후 소비한 만큼 upstream에 다시 요청한다")
        void shouldReplenishAfterEmitting() {
            // Given: 버퍼 크기 5
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 5, OverflowStrategy.DROP_LATEST);
            var upstreamSubscription = new ManualSubscription();

            buffered.onSubscribe(upstreamSubscription);
            // → prefetch: request(5) 발생

            // 버퍼에 데이터 5개 채우기
            for (int i = 1; i <= 5; i++) {
                buffered.onNext(i);
            }
            // buffer: [1, 2, 3, 4, 5]

            // When: downstream이 3개 요청
            downstream.request(3);

            // Then: 
            // - downstream에 3개 전달됨
            assertThat(downstream.getReceivedItems())
                    .as("downstream에 3개 전달됨")
                    .containsExactly(1, 2, 3);

            // - upstream에 replenish로 3개 추가 요청됨
            assertThat(upstreamSubscription.getRequestHistory())
                    .as("request 히스토리: prefetch(5) + replenish(3)")
                    .containsExactly(5L, 3L);

            assertThat(upstreamSubscription.getRequestedCount())
                    .as("총 요청량: 5 + 3 = 8")
                    .isEqualTo(8);
        }

        /**
         * <pre>
         * 여러 번 request와 replenish
         * ─────────────────────────────
         * downstream이 여러 번 request하면, 매번 replenish가 발생합니다.
         * </pre>
         */
        @Test
        @DisplayName("여러 번 request하면 매번 replenish가 발생한다")
        void shouldReplenishOnEachRequest() {
            // Given: 버퍼 크기 10
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);
            var upstreamSubscription = new ManualSubscription();

            buffered.onSubscribe(upstreamSubscription);
            // → prefetch: request(10)

            // 버퍼 채우기
            for (int i = 1; i <= 10; i++) {
                buffered.onNext(i);
            }

            // When: downstream이 여러 번 request
            downstream.request(2);  // 2개 소비 → replenish(2)
            downstream.request(3);  // 3개 소비 → replenish(3)
            downstream.request(1);  // 1개 소비 → replenish(1)

            // Then: request 히스토리 확인
            assertThat(upstreamSubscription.getRequestHistory())
                    .as("prefetch(10) + replenish(2) + replenish(3) + replenish(1)")
                    .containsExactly(10L, 2L, 3L, 1L);

            assertThat(downstream.getReceivedItems())
                    .as("총 6개 전달됨")
                    .containsExactly(1, 2, 3, 4, 5, 6);
        }

        /**
         * <pre>
         * upstream 완료 후에는 replenish 안 함
         * ──────────────────────────────────────
         * upstream이 onComplete를 보내면 더 이상 데이터가 없으므로
         * replenish를 하지 않습니다.
         * </pre>
         */
        @Test
        @DisplayName("upstream 완료 후에는 replenish하지 않는다")
        void shouldNotReplenishAfterUpstreamComplete() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 5, OverflowStrategy.DROP_LATEST);
            var upstreamSubscription = new ManualSubscription();

            buffered.onSubscribe(upstreamSubscription);
            // → prefetch: request(5)

            // 3개만 발행하고 완료
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onComplete();  // upstream 완료!

            // When: downstream이 request
            downstream.request(2);

            // Then: replenish 안 함 (upstream 이미 완료)
            assertThat(upstreamSubscription.getRequestHistory())
                    .as("prefetch만 있고 replenish 없음")
                    .containsExactly(5L);

            assertThat(downstream.getReceivedItems())
                    .containsExactly(1, 2);
        }
    }

    // =========================================================================
    // 3. OVERFLOW (버퍼 초과 처리)
    // =========================================================================

    @Nested
    @DisplayName("3. Overflow 동작")
    class OverflowBehavior {

        /**
         * <pre>
         * Overflow가 발생하는 경우
         * ───────────────────────────
         * 1. Upstream이 규약을 어겨서 request보다 많이 발행
         * 2. 동기적 Publisher에서 replenish 호출 시 즉시 onNext 발생
         *
         *     buffer: [1][2][3]  (가득 참!)
         *                │
         *                ▼
         *     새 데이터 '4' 도착 → Overflow!
         * </pre>
         */
        @Test
        @DisplayName("버퍼가 가득 찬 상태에서 데이터가 도착하면 overflow")
        void overflowOccursWhenBufferIsFull() {
            // Given: 버퍼 크기 3
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_OLDEST);
            var subscription = new ManualSubscription();

            buffered.onSubscribe(subscription);

            // When: 버퍼 크기(3)를 초과하여 5개 발행
            buffered.onNext(1);  // buffer: [1]
            buffered.onNext(2);  // buffer: [1, 2]
            buffered.onNext(3);  // buffer: [1, 2, 3] ← 가득 참!
            buffered.onNext(4);  // overflow! DROP_OLDEST → [2, 3, 4]
            buffered.onNext(5);  // overflow! DROP_OLDEST → [3, 4, 5]
            buffered.onComplete();

            // Then: downstream이 request하면 최신 3개만 받음
            downstream.request(Long.MAX_VALUE);

            assertThat(downstream.getReceivedItems())
                    .as("DROP_OLDEST: 오래된 1, 2 버려지고 최신 3, 4, 5만 남음")
                    .containsExactly(3, 4, 5);
        }

        /**
         * <pre>
         * DROP_OLDEST vs DROP_LATEST 비교
         * ─────────────────────────────────
         *
         * Buffer: [A][B][C] (가득 참!)
         * 새 데이터 'D' 도착
         *
         * DROP_OLDEST: [B][C][D]  (A 버림) - 최신 데이터 유지
         * DROP_LATEST: [A][B][C]  (D 버림) - 기존 데이터 유지
         * </pre>
         */
        @Test
        @DisplayName("DROP_OLDEST는 오래된 데이터를 버린다")
        void dropOldestRemovesOldData() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_OLDEST);
            var subscription = new ManualSubscription();

            buffered.onSubscribe(subscription);

            // When
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4);  // 1 버림
            buffered.onNext(5);  // 2 버림
            buffered.onComplete();

            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getReceivedItems())
                    .as("최신 데이터 [3, 4, 5] 유지")
                    .containsExactly(3, 4, 5);
        }

        @Test
        @DisplayName("DROP_LATEST는 새로운 데이터를 버린다")
        void dropLatestRemovesNewData() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_LATEST);
            var subscription = new ManualSubscription();

            buffered.onSubscribe(subscription);

            // When
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4);  // 4 버림
            buffered.onNext(5);  // 5 버림
            buffered.onComplete();

            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getReceivedItems())
                    .as("기존 데이터 [1, 2, 3] 유지")
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("ERROR 전략은 overflow 시 에러를 발생시킨다")
        void errorStrategyThrowsOnOverflow() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.ERROR);
            var subscription = new ManualSubscription();

            buffered.onSubscribe(subscription);

            // When
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            buffered.onNext(4);  // overflow!

            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getError())
                    .as("BufferOverflowException 발생")
                    .isInstanceOf(BufferOverflowException.class);

            assertThat(subscription.isCancelled())
                    .as("upstream도 cancel됨")
                    .isTrue();
        }

        /**
         * <pre>
         * 전략 선택 가이드
         * ─────────────────
         *
         * DROP_OLDEST: 실시간 모니터링, 최신 데이터가 중요한 경우
         *   예: 주식 시세, 센서 데이터, 실시간 위치
         *
         * DROP_LATEST: 순서 유지가 중요한 경우
         *   예: 로그 처리, 이벤트 소싱, 배치 처리
         *
         * ERROR: 데이터 손실이 허용되지 않는 경우
         *   예: 금융 거래, 주문 처리, 중요 알림
         * </pre>
         */
        @Test
        @DisplayName("사용 사례: 실시간 모니터링에는 DROP_OLDEST")
        void realTimeMonitoringUsesDropOldest() {
            // 시나리오: 센서에서 초당 100개 데이터가 오지만
            //          UI는 초당 10개만 표시 가능

            var ui = new TestSubscriber<String>();
            var buffered = new BufferedSubscriber<>(ui, 5, OverflowStrategy.DROP_OLDEST);
            var subscription = new ManualSubscription();

            buffered.onSubscribe(subscription);

            // 센서 데이터 10개 도착 (버퍼 5개보다 많음)
            buffered.onNext("temp=20");
            buffered.onNext("temp=21");
            buffered.onNext("temp=22");
            buffered.onNext("temp=23");
            buffered.onNext("temp=24");
            buffered.onNext("temp=25");  // overflow, temp=20 버림
            buffered.onNext("temp=26");  // overflow, temp=21 버림
            buffered.onComplete();

            // UI가 데이터 요청
            ui.request(Long.MAX_VALUE);

            // 최신 5개만 표시 (사용자는 최신 온도를 봐야 함)
            assertThat(ui.getReceivedItems())
                    .containsExactly("temp=22", "temp=23", "temp=24", "temp=25", "temp=26");
        }
    }

    // =========================================================================
    // 4. 전체 흐름 통합 테스트
    // =========================================================================

    @Nested
    @DisplayName("4. 전체 흐름 통합")
    class IntegrationFlow {

        /**
         * <pre>
         * 전체 흐름 시나리오
         * ───────────────────
         *
         * t=0: 구독 시작
         *      → prefetch: upstream.request(3)
         *
         * t=1: upstream이 1, 2, 3 발행
         *      → buffer: [1, 2, 3]
         *
         * t=2: downstream.request(2)
         *      → downstream에 1, 2 전달
         *      → replenish: upstream.request(2)
         *      → buffer: [3]
         *
         * t=3: upstream이 4, 5 발행
         *      → buffer: [3, 4, 5]
         *
         * t=4: downstream.request(10)
         *      → downstream에 3, 4, 5 전달
         *      → onComplete
         * </pre>
         */
        @Test
        @DisplayName("Prefetch → Buffering → Drain → Replenish 전체 흐름")
        void completeFlowWithPrefetchAndReplenish() {
            // Given
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 3, OverflowStrategy.DROP_LATEST);
            var upstreamSubscription = new ManualSubscription();

            // === t=0: 구독 시작 ===
            buffered.onSubscribe(upstreamSubscription);

            assertThat(upstreamSubscription.getRequestHistory())
                    .as("t=0: prefetch 발생")
                    .containsExactly(3L);

            // === t=1: upstream이 데이터 발행 ===
            buffered.onNext(1);
            buffered.onNext(2);
            buffered.onNext(3);
            // buffer: [1, 2, 3]

            assertThat(downstream.getReceivedItems())
                    .as("t=1: downstream은 아직 request 안 해서 비어있음")
                    .isEmpty();

            // === t=2: downstream이 2개 요청 ===
            downstream.request(2);

            assertThat(downstream.getReceivedItems())
                    .as("t=2: 2개 전달됨")
                    .containsExactly(1, 2);

            assertThat(upstreamSubscription.getRequestHistory())
                    .as("t=2: prefetch(3) + replenish(2)")
                    .containsExactly(3L, 2L);

            // === t=3: upstream이 추가 데이터 발행 ===
            buffered.onNext(4);
            buffered.onNext(5);
            buffered.onComplete();
            // buffer: [3, 4, 5], upstream 완료

            // === t=4: downstream이 나머지 요청 ===
            downstream.request(10);

            assertThat(downstream.getReceivedItems())
                    .as("t=4: 모든 데이터 전달됨")
                    .containsExactly(1, 2, 3, 4, 5);

            assertThat(downstream.isCompleted())
                    .as("t=4: 완료됨")
                    .isTrue();

            // replenish는 upstream 완료 후 발생 안 함
            assertThat(upstreamSubscription.getRequestHistory())
                    .as("최종: prefetch(3) + replenish(2)만 있음")
                    .containsExactly(3L, 2L);
        }

        /**
         * <pre>
         * ArrayPublisher와의 통합
         * ─────────────────────────
         * 실제 Publisher와 함께 동작하는 예시입니다.
         * </pre>
         */
        @Test
        @DisplayName("ArrayPublisher와 BufferedSubscriber 통합")
        void integrationWithArrayPublisher() {
            // Given
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 4, OverflowStrategy.DROP_OLDEST);

            // When: 구독 (prefetch로 4개 요청됨)
            publisher.subscribe(buffered);

            // 아직 downstream은 request 안 함
            assertThat(downstream.getReceivedItems()).isEmpty();

            // When: 2개씩 요청
            downstream.request(2);
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2);

            downstream.request(2);
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2, 3, 4);

            // When: 나머지 모두 요청
            downstream.request(Long.MAX_VALUE);

            // Then
            assertThat(downstream.getReceivedItems())
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(downstream.isCompleted()).isTrue();
        }
    }

    // =========================================================================
    // 5. 핵심 개념 요약
    // =========================================================================

    @Nested
    @DisplayName("5. 핵심 개념 요약")
    class KeyConceptsSummary {

        /**
         * <pre>
         * ┌─────────────────────────────────────────────────────────────────┐
         * │                    BufferedSubscriber 핵심                      │
         * ├─────────────────────────────────────────────────────────────────┤
         * │                                                                 │
         * │  1. Prefetch: 구독 시 버퍼 크기만큼 미리 요청                    │
         * │     → 대기 시간 최소화                                          │
         * │                                                                 │
         * │  2. Buffering: 데이터 임시 저장                                 │
         * │     → 속도 차이 완충                                            │
         * │                                                                 │
         * │  3. Drain: downstream demand만큼 전달                           │
         * │     → 실제 속도는 downstream이 결정!                            │
         * │                                                                 │
         * │  4. Replenish: 전달한 만큼 다시 요청                            │
         * │     → 버퍼 유지                                                 │
         * │                                                                 │
         * │  5. Overflow: 버퍼 초과 시 전략 적용                            │
         * │     → DROP_OLDEST / DROP_LATEST / ERROR                        │
         * │                                                                 │
         * └─────────────────────────────────────────────────────────────────┘
         *
         * 핵심: BufferedSubscriber는 "속도 조절"이 아닌 "완충"!
         *       실제 속도는 downstream의 request(n)이 결정합니다.
         * </pre>
         */
        @Test
        @DisplayName("버퍼는 속도 조절이 아닌 완충 역할을 한다")
        void bufferIsForBufferingNotRateLimiting() {
            // Given: 느린 downstream (한 번에 1개씩만 요청)
            var downstream = new TestSubscriber<Integer>();
            var buffered = new BufferedSubscriber<>(downstream, 10, OverflowStrategy.DROP_LATEST);
            var publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);

            publisher.subscribe(buffered);

            // When: downstream이 1개씩 천천히 요청
            downstream.request(1);
            assertThat(downstream.getReceivedItems()).containsExactly(1);

            downstream.request(1);
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2);

            downstream.request(1);
            assertThat(downstream.getReceivedItems()).containsExactly(1, 2, 3);

            // Then: 속도는 downstream의 request가 결정!
            // 버퍼가 있어도 downstream이 request(1)만 하면 1개씩만 받음
        }
    }
}
