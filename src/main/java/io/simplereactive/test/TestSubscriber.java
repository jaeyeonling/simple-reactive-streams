package io.simplereactive.test;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 Subscriber 구현.
 *
 * <p>수신한 시그널을 기록하여 테스트에서 검증할 수 있게 합니다.
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다. 여러 스레드에서 동시에 시그널을
 * 수신해도 안전하게 기록됩니다.
 *
 * @param <T> 수신할 요소의 타입
 */
public class TestSubscriber<T> implements Subscriber<T> {

    private final List<T> receivedItems = new CopyOnWriteArrayList<>();
    private final AtomicReference<Subscription> subscription = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription.set(s);
    }

    @Override
    public void onNext(T item) {
        receivedItems.add(item);
    }

    @Override
    public void onError(Throwable t) {
        this.error.set(t);
        completionLatch.countDown();
    }

    @Override
    public void onComplete() {
        this.completed.set(true);
        completionLatch.countDown();
    }

    // ===== Helper Methods =====

    /**
     * n개의 요소를 요청합니다.
     *
     * @param n 요청할 개수
     * @return this (메서드 체이닝용)
     */
    public TestSubscriber<T> request(long n) {
        Subscription s = subscription.get();
        if (s != null) {
            s.request(n);
        }
        return this;
    }

    /**
     * 구독을 취소합니다.
     *
     * @return this (메서드 체이닝용)
     */
    public TestSubscriber<T> cancel() {
        Subscription s = subscription.get();
        if (s != null) {
            s.cancel();
        }
        return this;
    }

    /**
     * 완료(onComplete 또는 onError)될 때까지 대기합니다.
     *
     * @return this (메서드 체이닝용)
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    public TestSubscriber<T> await() throws InterruptedException {
        completionLatch.await();
        return this;
    }

    /**
     * 지정된 시간 동안 완료를 대기합니다.
     *
     * @param timeout 대기 시간
     * @param unit 시간 단위
     * @return this (메서드 체이닝용)
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    public TestSubscriber<T> await(long timeout, TimeUnit unit) throws InterruptedException {
        completionLatch.await(timeout, unit);
        return this;
    }

    // ===== Getters =====

    /**
     * 수신한 요소 목록을 반환합니다.
     *
     * <p>반환된 목록은 불변입니다.
     *
     * @return 수신한 요소들의 불변 목록
     */
    public List<T> getReceivedItems() {
        return Collections.unmodifiableList(receivedItems);
    }

    /**
     * 수신한 요소 목록을 반환합니다.
     *
     * <p>{@link #getReceivedItems()}의 단축 메서드입니다.
     *
     * @return 수신한 요소들의 불변 목록
     */
    public List<T> getItems() {
        return getReceivedItems();
    }

    /**
     * 완료 여부를 반환합니다.
     *
     * @return onComplete가 호출되었으면 true
     */
    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * 에러 발생 여부를 반환합니다.
     *
     * @return onError가 호출되었으면 true
     */
    public boolean hasError() {
        return error.get() != null;
    }

    /**
     * 발생한 에러를 반환합니다.
     *
     * @return 발생한 에러, 없으면 null
     */
    public Throwable getError() {
        return error.get();
    }

    /**
     * Subscription을 반환합니다.
     *
     * @return 현재 Subscription, 없으면 null
     */
    public Subscription getSubscription() {
        return subscription.get();
    }

    /**
     * 수신한 요소 개수를 반환합니다.
     *
     * @return 수신한 요소 개수
     */
    public int getItemCount() {
        return receivedItems.size();
    }
}
