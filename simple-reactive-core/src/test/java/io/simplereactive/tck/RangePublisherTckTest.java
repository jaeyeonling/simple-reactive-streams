package io.simplereactive.tck;

import io.simplereactive.publisher.ErrorPublisher;
import io.simplereactive.publisher.RangePublisher;
import io.simplereactive.tck.adapter.PublisherAdapter;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * RangePublisher의 Reactive Streams TCK 검증 테스트.
 *
 * <p>RangePublisher는 정수 범위를 발행하는 유한 Publisher입니다.
 * TCK를 통해 Backpressure, 취소, 에러 처리 등의 규약 준수를 검증합니다.
 *
 * @see RangePublisher
 */
public class RangePublisherTckTest extends PublisherVerification<Integer> {

    private static final long DEFAULT_TIMEOUT_MILLIS = 100L;
    private static final long NO_SIGNAL_TIMEOUT_MILLIS = 100L;

    public RangePublisherTckTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, NO_SIGNAL_TIMEOUT_MILLIS));
    }

    /**
     * TCK가 테스트할 RangePublisher를 생성합니다.
     *
     * @param elements 발행할 요소 수
     * @return 어댑팅된 Publisher
     */
    @Override
    public Publisher<Integer> createPublisher(long elements) {
        // 0부터 시작하여 elements 개수만큼 발행
        return new PublisherAdapter<>(new RangePublisher(0, (int) elements));
    }

    /**
     * 실패하는 Publisher를 생성합니다.
     *
     * @return 실패하는 Publisher
     */
    @Override
    public Publisher<Integer> createFailedPublisher() {
        return new PublisherAdapter<>(
                new ErrorPublisher<>(new RuntimeException("TCK test error")));
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1000L;
    }
}
