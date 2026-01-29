package io.simplereactive.test;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 테스트용 Subscriber 구현.
 * 
 * <p>수신한 시그널을 기록하여 테스트에서 검증할 수 있게 합니다.</p>
 * 
 * @param <T> 수신할 요소의 타입
 */
public class TestSubscriber<T> implements Subscriber<T> {
    
    private final List<T> receivedItems = new ArrayList<>();
    private Subscription subscription;
    private Throwable error;
    private boolean completed = false;
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    
    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
    }
    
    @Override
    public void onNext(T item) {
        receivedItems.add(item);
    }
    
    @Override
    public void onError(Throwable t) {
        this.error = t;
        completionLatch.countDown();
    }
    
    @Override
    public void onComplete() {
        this.completed = true;
        completionLatch.countDown();
    }
    
    // ===== Helper Methods =====
    
    /**
     * n개의 요소를 요청합니다.
     */
    public TestSubscriber<T> request(long n) {
        if (subscription != null) {
            subscription.request(n);
        }
        return this;
    }
    
    /**
     * 구독을 취소합니다.
     */
    public TestSubscriber<T> cancel() {
        if (subscription != null) {
            subscription.cancel();
        }
        return this;
    }
    
    /**
     * 완료될 때까지 대기합니다.
     */
    public TestSubscriber<T> await() throws InterruptedException {
        completionLatch.await();
        return this;
    }
    
    /**
     * 지정된 시간 동안 완료를 대기합니다.
     */
    public TestSubscriber<T> await(long timeout, TimeUnit unit) throws InterruptedException {
        completionLatch.await(timeout, unit);
        return this;
    }
    
    // ===== Getters =====
    
    /**
     * 수신한 요소 목록을 반환합니다.
     */
    public List<T> getReceivedItems() {
        return Collections.unmodifiableList(receivedItems);
    }
    
    /**
     * 완료 여부를 반환합니다.
     */
    public boolean isCompleted() {
        return completed;
    }
    
    /**
     * 발생한 에러를 반환합니다.
     */
    public Throwable getError() {
        return error;
    }
    
    /**
     * Subscription을 반환합니다.
     */
    public Subscription getSubscription() {
        return subscription;
    }
}
