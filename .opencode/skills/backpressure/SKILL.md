---
name: backpressure
description: Backpressure 개념, 전략, 구현 가이드
---

# Backpressure Guide

## Backpressure란?

**Backpressure**는 데이터 생산자(Publisher)가 소비자(Subscriber)보다 빠를 때 
발생하는 문제를 해결하는 메커니즘입니다.

### 비유: 물탱크와 수도꼭지

```
    빠른 생산자 (수도꼭지)
         💧💧💧💧💧
            ↓↓↓↓↓
    ┌─────────────────┐
    │   Buffer Tank   │  ← 버퍼가 가득 차면?
    │  💧💧💧💧💧💧💧  │     1. 넘친다 (데이터 손실)
    └────────┬────────┘     2. 막힌다 (블로킹)
             ↓                3. 수도꼭지 잠근다 (Backpressure!)
    느린 소비자 (배수구)
         💧
```

## Pull vs Push 모델

### Push 모델 (문제 있음)

```
Publisher                    Subscriber
    │                            │
    │────── data ───────────────>│
    │────── data ───────────────>│
    │────── data ───────────────>│  ← 처리 못함!
    │────── data ───────────────>│  ← OutOfMemory!
    │                            │
```

### Pull 모델 (Reactive Streams)

```
Subscriber                   Publisher
    │                            │
    │────── request(2) ─────────>│  "2개 줘"
    │<─────── data ──────────────│
    │<─────── data ──────────────│
    │                            │
    │────── request(1) ─────────>│  "1개 더 줘"
    │<─────── data ──────────────│
    │                            │
```

## request(n)의 의미

```java
subscription.request(n);
```

- **의미**: "나는 n개를 처리할 준비가 됐어"
- **효과**: Publisher는 최대 n개까지만 onNext 호출 가능
- **누적**: request(3) + request(2) = 총 5개 요청

### Demand 관리

```java
public class DemandTracker {
    private final AtomicLong demand = new AtomicLong(0);
    
    public void request(long n) {
        // demand 추가 (overflow 방지)
        long current;
        long next;
        do {
            current = demand.get();
            if (current == Long.MAX_VALUE) {
                return;  // unbounded
            }
            next = current + n;
            if (next < 0) {
                next = Long.MAX_VALUE;  // overflow → unbounded
            }
        } while (!demand.compareAndSet(current, next));
    }
    
    public boolean tryConsume() {
        long current;
        do {
            current = demand.get();
            if (current == 0) {
                return false;  // demand 없음
            }
            if (current == Long.MAX_VALUE) {
                return true;  // unbounded
            }
        } while (!demand.compareAndSet(current, current - 1));
        return true;
    }
}
```

## Backpressure 전략

### 1. Buffer (버퍼링)

```
┌─────────────────────────────────────┐
│  Publisher → [Buffer] → Subscriber  │
│                                     │
│  request(n)이 올 때까지 버퍼에 저장   │
│  버퍼가 가득 차면? → 전략 선택        │
└─────────────────────────────────────┘
```

```java
public class BufferedSubscription<T> implements Subscription {
    private final Queue<T> buffer = new ArrayDeque<>();
    private final int maxSize;
    
    @Override
    public void request(long n) {
        while (n > 0 && !buffer.isEmpty()) {
            subscriber.onNext(buffer.poll());
            n--;
        }
    }
    
    public void onProduced(T item) {
        if (buffer.size() >= maxSize) {
            // 전략에 따라 처리
        }
        buffer.offer(item);
    }
}
```

### 2. Drop Oldest (오래된 것 버림)

```java
public void onProduced(T item) {
    if (buffer.size() >= maxSize) {
        buffer.poll();  // 가장 오래된 것 제거
    }
    buffer.offer(item);
}
```

### 3. Drop Latest (새로운 것 버림)

```java
public void onProduced(T item) {
    if (buffer.size() >= maxSize) {
        return;  // 새 아이템 무시
    }
    buffer.offer(item);
}
```

### 4. Error (에러 발생)

```java
public void onProduced(T item) {
    if (buffer.size() >= maxSize) {
        subscriber.onError(
            new IllegalStateException("Buffer overflow")
        );
        return;
    }
    buffer.offer(item);
}
```

### 5. Block (블로킹)

```java
public void onProduced(T item) throws InterruptedException {
    while (buffer.size() >= maxSize) {
        Thread.sleep(10);  // 공간이 생길 때까지 대기
        // 주의: 데드락 가능!
    }
    buffer.offer(item);
}
```

## 구현 예제: ArrayPublisher with Backpressure

```java
public class ArrayPublisher<T> implements Publisher<T> {
    private final T[] array;
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new ArraySubscription<>(subscriber, array));
    }
    
    static class ArraySubscription<T> implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final T[] array;
        private int index = 0;
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("n must be > 0"));
                return;
            }
            
            // demand 추가
            long current;
            long next;
            do {
                current = requested.get();
                next = current + n;
                if (next < 0) next = Long.MAX_VALUE;
            } while (!requested.compareAndSet(current, next));
            
            // 데이터 발행
            drain();
        }
        
        private void drain() {
            while (requested.get() > 0 && index < array.length) {
                if (cancelled.get()) {
                    return;
                }
                
                T item = array[index++];
                subscriber.onNext(item);
                
                // demand 감소 (unbounded가 아닌 경우)
                if (requested.get() != Long.MAX_VALUE) {
                    requested.decrementAndGet();
                }
            }
            
            // 완료 체크
            if (index >= array.length && !cancelled.get()) {
                subscriber.onComplete();
            }
        }
        
        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
```

## Unbounded Request

`request(Long.MAX_VALUE)`는 "무제한으로 달라"는 의미입니다.

```java
// Backpressure 비활성화 (주의!)
subscription.request(Long.MAX_VALUE);
```

**사용 시기**:
- Subscriber가 충분히 빠를 때
- 메모리가 충분할 때
- 테스트 목적

## 관련 스킬

- `reactive-spec`: 규약 상세
- `operator-pattern`: Operator에서의 Backpressure
