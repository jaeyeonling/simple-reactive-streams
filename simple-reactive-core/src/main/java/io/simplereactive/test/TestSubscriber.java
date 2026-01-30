package io.simplereactive.test;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 Subscriber 구현.
 *
 * <p>수신한 시그널을 기록하여 테스트에서 검증할 수 있게 합니다.
 * Reactive Streams 규약 위반도 감지합니다.
 *
 * <h2>스레드 안전성</h2>
 * <p>이 클래스는 스레드 안전합니다. 여러 스레드에서 동시에 시그널을
 * 수신해도 안전하게 기록됩니다.
 *
 * <h2>규약 검증</h2>
 * <p>다음 규약 위반을 감지합니다:
 * <ul>
 *   <li>Rule 1.7: onError/onComplete 후 추가 시그널</li>
 *   <li>Rule 2.3: onSubscribe가 여러 번 호출됨</li>
 *   <li>Rule 2.13: null 값 전달</li>
 * </ul>
 *
 * @param <T> 수신할 요소의 타입
 */
public class TestSubscriber<T> implements Subscriber<T> {

    private final List<T> receivedItems = new CopyOnWriteArrayList<>();
    private final List<String> violations = new CopyOnWriteArrayList<>();
    private final AtomicReference<Subscription> subscription = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicInteger onSubscribeCount = new AtomicInteger(0);
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Override
    public void onSubscribe(Subscription s) {
        int count = onSubscribeCount.incrementAndGet();
        
        // Rule 2.3: onSubscribe는 최대 한 번만 호출되어야 함
        if (count > 1) {
            violations.add("Rule 2.3: onSubscribe called " + count + " times");
            // 두 번째 이후 Subscription은 cancel
            if (s != null) {
                s.cancel();
            }
            return;
        }
        
        // Rule 2.13: Subscription이 null이면 안 됨
        if (s == null) {
            violations.add("Rule 2.13: onSubscribe called with null Subscription");
            return;
        }
        
        this.subscription.set(s);
    }

    @Override
    public void onNext(T item) {
        // Rule 1.7: 종료 후 시그널 검증
        if (terminated.get()) {
            violations.add("Rule 1.7: onNext called after terminal signal");
            return;
        }
        
        // Rule 2.13: null 검증
        if (item == null) {
            violations.add("Rule 2.13: onNext called with null");
            return;
        }
        
        receivedItems.add(item);
    }

    @Override
    public void onError(Throwable t) {
        // Rule 1.7: 종료 후 시그널 검증
        if (terminated.getAndSet(true)) {
            violations.add("Rule 1.7: onError called after terminal signal");
            return;
        }
        
        // Rule 2.13: null 검증
        if (t == null) {
            violations.add("Rule 2.13: onError called with null");
        }
        
        this.error.set(t);
        completionLatch.countDown();
    }

    @Override
    public void onComplete() {
        // Rule 1.7: 종료 후 시그널 검증
        if (terminated.getAndSet(true)) {
            violations.add("Rule 1.7: onComplete called after terminal signal");
            return;
        }
        
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

    // ===== 규약 검증 메서드 =====

    /**
     * 규약 위반이 있었는지 확인합니다.
     *
     * @return 규약 위반이 있으면 true
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /**
     * 감지된 규약 위반 목록을 반환합니다.
     *
     * @return 규약 위반 메시지 목록 (불변)
     */
    public List<String> getViolations() {
        return Collections.unmodifiableList(new ArrayList<>(violations));
    }

    /**
     * onSubscribe 호출 횟수를 반환합니다.
     *
     * @return onSubscribe 호출 횟수
     */
    public int getOnSubscribeCount() {
        return onSubscribeCount.get();
    }

    /**
     * 종료 상태인지 확인합니다.
     *
     * @return onComplete 또는 onError가 호출되었으면 true
     */
    public boolean isTerminated() {
        return terminated.get();
    }
}
