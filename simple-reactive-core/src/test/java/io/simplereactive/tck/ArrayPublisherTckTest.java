package io.simplereactive.tck;

import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.publisher.ErrorPublisher;
import io.simplereactive.tck.adapter.PublisherAdapter;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * ArrayPublisher의 Reactive Streams TCK 검증 테스트.
 *
 * <p>TCK(Technology Compatibility Kit)는 Reactive Streams 스펙의 공식 테스트 스위트입니다.
 * 이 테스트를 통과하면 구현체가 스펙을 올바르게 준수함을 보장합니다.
 *
 * <h2>TCK가 검증하는 규약들</h2>
 * <ul>
 *   <li>Rule 1.x: Publisher 규약 (onSubscribe 호출, null 체크 등)</li>
 *   <li>Rule 2.x: Subscriber 규약 (시그널 순서, 종료 상태 등)</li>
 *   <li>Rule 3.x: Subscription 규약 (request, cancel 동작)</li>
 * </ul>
 *
 * <h2>테스트 환경</h2>
 * <p>TestEnvironment는 비동기 테스트를 위한 타임아웃 설정을 제공합니다.
 * 기본 타임아웃은 100ms이며, CI 환경에서는 더 길게 설정할 수 있습니다.
 *
 * @see PublisherVerification
 * @see ArrayPublisher
 */
public class ArrayPublisherTckTest extends PublisherVerification<Integer> {

    /**
     * 테스트 환경 타임아웃 (밀리초).
     * 비동기 시그널이 이 시간 내에 도착해야 합니다.
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = 100L;

    /**
     * 무한 Publisher 테스트 시 발행되지 않는 시간 타임아웃.
     */
    private static final long NO_SIGNAL_TIMEOUT_MILLIS = 100L;

    public ArrayPublisherTckTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, NO_SIGNAL_TIMEOUT_MILLIS));
    }

    /**
     * TCK가 테스트할 Publisher를 생성합니다.
     *
     * <p>elements 개수만큼의 요소를 발행하는 Publisher를 반환해야 합니다.
     * ArrayPublisher는 유한 Publisher이므로 요청된 개수만큼 배열을 생성합니다.
     *
     * @param elements 발행할 요소 수
     * @return 어댑팅된 Publisher
     */
    @Override
    public Publisher<Integer> createPublisher(long elements) {
        // elements 개수만큼의 Integer 배열 생성
        Integer[] array = new Integer[(int) elements];
        for (int i = 0; i < elements; i++) {
            array[i] = i;
        }
        
        // 우리의 ArrayPublisher를 org.reactivestreams.Publisher로 어댑팅
        return new PublisherAdapter<>(new ArrayPublisher<>(array));
    }

    /**
     * 실패하는 Publisher를 생성합니다.
     *
     * <p>TCK는 에러 시나리오도 테스트합니다.
     * ErrorPublisher를 사용하여 구독 즉시 에러를 발생시킵니다.
     *
     * @return 실패하는 Publisher
     */
    @Override
    public Publisher<Integer> createFailedPublisher() {
        return new PublisherAdapter<>(
                new ErrorPublisher<>(new RuntimeException("TCK test error")));
    }

    /**
     * 이 Publisher가 발행할 수 있는 최대 요소 수를 반환합니다.
     *
     * <p>ArrayPublisher는 유한 Publisher이므로 배열 최대 크기를 반환합니다.
     * TCK는 이 값을 사용하여 무한 vs 유한 Publisher 테스트를 구분합니다.
     *
     * @return 최대 요소 수 (Long.MAX_VALUE면 무한)
     */
    @Override
    public long maxElementsFromPublisher() {
        // Integer.MAX_VALUE 크기의 배열은 메모리 문제가 있으므로 적당한 값 반환
        return 1000L;
    }
}
