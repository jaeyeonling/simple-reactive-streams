package io.simplereactive.core;

/**
 * Publisher이자 Subscriber인 처리기.
 * 
 * <p>Processor는 데이터를 수신하여 변환한 후 다시 발행합니다.
 * Publisher와 Subscriber의 모든 규약을 준수해야 합니다 (Rule 4.1).</p>
 * 
 * @param <T> 입력 데이터 타입
 * @param <R> 출력 데이터 타입
 * @see Publisher
 * @see Subscriber
 */
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
