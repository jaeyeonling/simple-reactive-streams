# Module 2: 첫 번째 Publisher 구현

> ⏱️ 예상 시간: 2시간 | ★★★☆☆ 난이도

## 학습 목표

- ArrayPublisher를 직접 구현하며 Publisher의 동작 원리 이해
- Subscription 상태 관리 학습
- request(n) 기반 데이터 발행 구현

---

## 1. 문제 제시

### 시나리오

정수 배열 `[1, 2, 3, 4, 5]`를 Reactive Stream으로 발행하는 Publisher를 만들어야 합니다.

### 요구사항

```
Subscriber가 request(2) → onNext(1), onNext(2)
Subscriber가 request(3) → onNext(3), onNext(4), onNext(5), onComplete()
```

### 기대 동작

```
Subscriber                    Publisher (ArrayPublisher)
    │                             │
    │──── subscribe ─────────────>│
    │<─── onSubscribe ────────────│
    │                             │
    │──── request(2) ────────────>│
    │<─── onNext(1) ──────────────│
    │<─── onNext(2) ──────────────│
    │                             │
    │──── request(3) ────────────>│
    │<─── onNext(3) ──────────────│
    │<─── onNext(4) ──────────────│
    │<─── onNext(5) ──────────────│
    │<─── onComplete() ───────────│
```

---

## 2. 개념 설명

### Subscription의 상태

Subscription은 상태를 가집니다:

```
┌─────────────────────────────────────────────────────┐
│                 Subscription 상태                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│   ┌─────────┐                                       │
│   │ WAITING │  subscribe 전                         │
│   └────┬────┘                                       │
│        │ subscribe()                                │
│        ▼                                            │
│   ┌──────────┐                                      │
│   │SUBSCRIBED│  활성 상태                            │
│   └────┬─────┘                                      │
│        │                                            │
│   ┌────┴────┬──────────┐                            │
│   │         │          │                            │
│   ▼         ▼          ▼                            │
│ ┌─────┐ ┌─────┐  ┌──────────┐                       │
│ │ERROR│ │DONE │  │CANCELLED │                       │
│ └─────┘ └─────┘  └──────────┘                       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Demand 관리

`request(n)`이 호출되면 "n개를 보내도 된다"는 의미입니다:

```
request(3)  → demanded = 3
onNext(1)   → demanded = 2
onNext(2)   → demanded = 1
onNext(3)   → demanded = 0  (더 이상 못 보냄)
request(2)  → demanded = 2
onNext(4)   → demanded = 1
...
```

### 동시성 고려

여러 스레드에서 `request()`가 호출될 수 있습니다:

```java
// 잘못된 방식 - 경쟁 조건!
private long requested = 0;

public void request(long n) {
    requested += n;  // 스레드 안전하지 않음!
}

// 올바른 방식 - Atomic 사용
private final AtomicLong requested = new AtomicLong(0);

public void request(long n) {
    requested.addAndGet(n);  // 스레드 안전
}
```

---

## 3. 구현 가이드

### Step 1: 기본 구조

`src/main/java/io/simplereactive/publisher/ArrayPublisher.java`:

```java
package io.simplereactive.publisher;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 배열의 요소를 순서대로 발행하는 Publisher.
 * 
 * Cold Publisher로, 구독할 때마다 처음부터 발행합니다.
 */
public class ArrayPublisher<T> implements Publisher<T> {
    
    private final T[] array;
    
    @SafeVarargs
    public ArrayPublisher(T... array) {
        this.array = array;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // TODO: Rule 1.9 - null 체크
        
        // TODO: Subscription 생성 및 onSubscribe 호출
    }
    
    /**
     * ArrayPublisher의 Subscription 구현.
     */
    static class ArraySubscription<T> implements Subscription {
        
        private final Subscriber<? super T> subscriber;
        private final T[] array;
        
        private int index = 0;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        ArraySubscription(Subscriber<? super T> subscriber, T[] array) {
            this.subscriber = subscriber;
            this.array = array;
        }
        
        @Override
        public void request(long n) {
            // TODO: Rule 3.9 - n <= 0 체크
            
            // TODO: demand 추가
            
            // TODO: 데이터 발행
        }
        
        @Override
        public void cancel() {
            // TODO: 취소 처리
        }
        
        /**
         * 요청된 만큼 데이터를 발행합니다.
         */
        private void drain() {
            // TODO: 구현
        }
    }
}
```

### Step 2: subscribe 구현

```java
@Override
public void subscribe(Subscriber<? super T> subscriber) {
    // Rule 1.9: null 체크
    if (subscriber == null) {
        throw new NullPointerException("Subscriber must not be null");
    }
    
    // Rule 1.1: onSubscribe 호출
    ArraySubscription<T> subscription = new ArraySubscription<>(subscriber, array);
    subscriber.onSubscribe(subscription);
}
```

### Step 3: request 구현

```java
@Override
public void request(long n) {
    // Rule 3.9: n <= 0이면 에러
    if (n <= 0) {
        subscriber.onError(
            new IllegalArgumentException("Rule 3.9: n must be positive")
        );
        return;
    }
    
    // demand 추가 (overflow 방지)
    long current, next;
    do {
        current = requested.get();
        if (current == Long.MAX_VALUE) {
            return;  // 이미 unbounded
        }
        next = current + n;
        if (next < 0) {
            next = Long.MAX_VALUE;  // overflow → unbounded
        }
    } while (!requested.compareAndSet(current, next));
    
    // 데이터 발행
    drain();
}
```

### Step 4: drain 구현

```java
private void drain() {
    // 이미 취소되었으면 중단
    if (cancelled.get()) {
        return;
    }
    
    // 요청된 만큼 발행
    while (requested.get() > 0 && index < array.length) {
        // 취소 체크
        if (cancelled.get()) {
            return;
        }
        
        T item = array[index++];
        
        // null 체크 (Rule 2.13)
        if (item == null) {
            subscriber.onError(
                new NullPointerException("Array element must not be null")
            );
            return;
        }
        
        subscriber.onNext(item);
        
        // demand 감소
        if (requested.get() != Long.MAX_VALUE) {
            requested.decrementAndGet();
        }
    }
    
    // 모든 요소 발행 완료
    if (index >= array.length && !cancelled.get()) {
        subscriber.onComplete();
    }
}
```

### Step 5: cancel 구현

```java
@Override
public void cancel() {
    cancelled.set(true);
}
```

---

## 4. 테스트

### 테스트 코드 작성

`src/test/java/io/simplereactive/publisher/ArrayPublisherTest.java`:

```java
package io.simplereactive.publisher;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ArrayPublisherTest {
    
    @Test
    @DisplayName("request(n)만큼만 onNext 호출")
    void shouldEmitOnlyRequestedElements() {
        // Given
        ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        
        // When
        publisher.subscribe(subscriber);
        subscriber.request(3);
        
        // Then
        assertThat(subscriber.received).containsExactly(1, 2, 3);
        assertThat(subscriber.completed).isFalse();
    }
    
    @Test
    @DisplayName("모든 요소 발행 후 onComplete 호출")
    void shouldCompleteAfterAllElements() {
        // Given
        ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        
        // When
        publisher.subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);
        
        // Then
        assertThat(subscriber.received).containsExactly(1, 2, 3);
        assertThat(subscriber.completed).isTrue();
    }
    
    @Test
    @DisplayName("cancel 후 시그널 중지")
    void shouldStopAfterCancel() {
        // Given
        ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        
        // When
        publisher.subscribe(subscriber);
        subscriber.request(2);
        subscriber.cancel();
        subscriber.request(10);
        
        // Then
        assertThat(subscriber.received).containsExactly(1, 2);
    }
    
    /**
     * 테스트용 Subscriber
     */
    static class TestSubscriber<T> implements Subscriber<T> {
        final List<T> received = new ArrayList<>();
        Subscription subscription;
        Throwable error;
        boolean completed;
        
        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
        }
        
        @Override
        public void onNext(T item) {
            received.add(item);
        }
        
        @Override
        public void onError(Throwable t) {
            this.error = t;
        }
        
        @Override
        public void onComplete() {
            this.completed = true;
        }
        
        void request(long n) {
            subscription.request(n);
        }
        
        void cancel() {
            subscription.cancel();
        }
    }
}
```

### 테스트 실행

```bash
./gradlew test --tests "ArrayPublisherTest"
```

---

## 5. 검증

```bash
# OpenCode에서:
/check 2
```

### 체크포인트

- [ ] ArrayPublisher 클래스 구현 완료
- [ ] subscribe() 메서드 구현 (Rule 1.1, 1.9)
- [ ] request() 메서드 구현 (Rule 3.9)
- [ ] cancel() 메서드 구현
- [ ] drain() 메서드 구현
- [ ] 테스트 통과

---

## 6. 심화 학습

### 생각해볼 문제

1. `drain()`을 여러 스레드에서 동시에 호출하면 어떻게 될까?
2. `request(Long.MAX_VALUE)`의 의미는?
3. 현재 구현에서 개선할 점은?

### 다음 도전

- RangePublisher 구현해보기
- EmptyPublisher, ErrorPublisher 구현해보기

---

## 다음 단계

Module 3에서는 Backpressure를 깊이 이해합니다.

```bash
# OpenCode에서:
/learn 3
```
