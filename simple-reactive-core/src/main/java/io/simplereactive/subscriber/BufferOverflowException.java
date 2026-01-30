package io.simplereactive.subscriber;

/**
 * 버퍼 오버플로우 발생 시 던져지는 예외.
 *
 * <p>{@link OverflowStrategy#ERROR} 전략 사용 시
 * 버퍼가 가득 찬 상태에서 새 데이터가 도착하면 발생합니다.
 *
 * @see BufferedSubscriber
 * @see OverflowStrategy#ERROR
 */
public class BufferOverflowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int bufferSize;

    /**
     * BufferOverflowException을 생성합니다.
     *
     * @param bufferSize 버퍼 크기
     */
    public BufferOverflowException(int bufferSize) {
        super("Buffer overflow: buffer size is " + bufferSize);
        this.bufferSize = bufferSize;
    }

    /**
     * BufferOverflowException을 생성합니다.
     *
     * @param message 에러 메시지
     * @param bufferSize 버퍼 크기
     */
    public BufferOverflowException(String message, int bufferSize) {
        super(message);
        this.bufferSize = bufferSize;
    }

    /**
     * 버퍼 크기를 반환합니다.
     *
     * @return 버퍼 크기
     */
    public int getBufferSize() {
        return bufferSize;
    }
}
