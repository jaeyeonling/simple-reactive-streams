# Module 3: Backpressure 깊이 이해하기

> ⏱️ 예상 시간: 2시간 | ★★★★☆ 난이도

## 학습 목표

- Backpressure가 왜 필요한지 이해
- request(n) 메커니즘 심층 학습
- 다양한 Backpressure 전략 구현

---

## 1. 문제 제시

### 시나리오

초당 1000건의 로그를 생성하는 서버가 있습니다.
파일에 저장하는 Consumer는 초당 100건만 처리할 수 있습니다.

```
┌─────────────────┐        ┌─────────────────┐
│   Log Server    │───────>│  File Writer    │
│  (1000건/초)    │        │  (100건/초)     │
└─────────────────┘        └─────────────────┘
        빠름                      느림
```

### 문제

1. 처리 못한 900건은 어디로 갈까?
2. 메모리가 무한정 증가하면?
3. 어떻게 해결할 수 있을까?

---

## 2. 개념 설명

### Backpressure란?

```
비유: 물탱크와 수도꼭지

    빠른 생산자 (수도꼭지)
         💧💧💧💧💧
            ↓↓↓↓↓
    ┌─────────────────┐
    │   Buffer Tank   │  ← 가득 차면?
    │  💧💧💧💧💧💧💧  │     
    └────────┬────────┘     
             ↓              
    느린 소비자 (배수구)
         💧

해결책:
1. 넘치게 둔다 (데이터 손실) ❌
2. 탱크를 막는다 (블로킹) ❌
3. 수도꼭지를 잠근다 (Backpressure) ✅
```

### Push vs Pull

```
Push 모델 (문제 있음):
┌──────────┐                   ┌──────────┐
│ Producer │──── 데이터 ──────>│ Consumer │
└──────────┘  "받아! 받아!"    └──────────┘
                              "너무 빨라!"

Pull 모델 (Reactive Streams):
┌──────────┐                   ┌──────────┐
│ Producer │<── request(n) ───│ Consumer │
└──────────┘                   └──────────┘
              "n개 줄 수 있어"   "n개 줘"
```

### request(n)의 의미

```java
subscription.request(n);
// = "나는 n개를 처리할 준비가 됐어"
// = "n개까지 보내도 돼"
// = "n개를 초과하면 안 돼!"
```

---

## 3. 구현 가이드

### Step 1: Demand 관리 클래스

```java
package io.simplereactive.subscription;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Backpressure를 위한 demand 관리.
 */
public class DemandTracker {
    
    private final AtomicLong demand = new AtomicLong(0);
    
    /**
     * demand를 추가합니다.
     * 
     * @param n 추가할 demand (양수)
     * @return 추가 후 총 demand
     */
    public long add(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        
        long current, next;
        do {
            current = demand.get();
            if (current == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            next = current + n;
            if (next < 0) {  // overflow
                next = Long.MAX_VALUE;
            }
        } while (!demand.compareAndSet(current, next));
        
        return next;
    }
    
    /**
     * demand를 1 감소시킵니다.
     * 
     * @return 성공 여부 (demand가 0이면 false)
     */
    public boolean tryConsume() {
        long current;
        do {
            current = demand.get();
            if (current == 0) {
                return false;
            }
            if (current == Long.MAX_VALUE) {
                return true;  // unbounded는 감소 안 함
            }
        } while (!demand.compareAndSet(current, current - 1));
        
        return true;
    }
    
    /**
     * 현재 demand를 반환합니다.
     */
    public long get() {
        return demand.get();
    }
    
    /**
     * unbounded인지 확인합니다.
     */
    public boolean isUnbounded() {
        return demand.get() == Long.MAX_VALUE;
    }
}
```

### Step 2: BufferedSubscriber 구현

버퍼를 사용하여 Backpressure를 처리하는 Subscriber:

```java
package io.simplereactive.subscriber;

import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 버퍼를 사용하는 Subscriber.
 * 버퍼가 가득 차면 지정된 전략에 따라 처리합니다.
 */
public class BufferedSubscriber<T> implements Subscriber<T> {
    
    public enum OverflowStrategy {
        DROP_OLDEST,  // 오래된 것 버림
        DROP_LATEST,  // 새로운 것 버림
        ERROR         // 에러 발생
    }
    
    private final Subscriber<T> downstream;
    private final Queue<T> buffer;
    private final int bufferSize;
    private final OverflowStrategy strategy;
    private Subscription upstream;
    
    public BufferedSubscriber(
            Subscriber<T> downstream, 
            int bufferSize,
            OverflowStrategy strategy) {
        this.downstream = downstream;
        this.bufferSize = bufferSize;
        this.buffer = new ArrayBlockingQueue<>(bufferSize);
        this.strategy = strategy;
    }
    
    @Override
    public void onSubscribe(Subscription s) {
        this.upstream = s;
        downstream.onSubscribe(new BufferedSubscription());
        // 버퍼 크기만큼 미리 요청
        s.request(bufferSize);
    }
    
    @Override
    public void onNext(T item) {
        if (!buffer.offer(item)) {
            // 버퍼가 가득 참
            switch (strategy) {
                case DROP_OLDEST:
                    buffer.poll();  // 오래된 것 제거
                    buffer.offer(item);
                    break;
                case DROP_LATEST:
                    // 새 아이템 무시
                    break;
                case ERROR:
                    upstream.cancel();
                    downstream.onError(
                        new IllegalStateException("Buffer overflow")
                    );
                    return;
            }
        }
        drain();
    }
    
    @Override
    public void onError(Throwable t) {
        downstream.onError(t);
    }
    
    @Override
    public void onComplete() {
        // 버퍼의 남은 아이템 모두 전달 후 완료
        drainAll();
        downstream.onComplete();
    }
    
    private void drain() {
        // TODO: downstream의 demand에 따라 버퍼에서 전달
    }
    
    private void drainAll() {
        T item;
        while ((item = buffer.poll()) != null) {
            downstream.onNext(item);
        }
    }
    
    class BufferedSubscription implements Subscription {
        @Override
        public void request(long n) {
            // downstream의 request 처리
            drain();
        }
        
        @Override
        public void cancel() {
            upstream.cancel();
        }
    }
}
```

---

## 4. Backpressure 시각화

### 정상 흐름

```
request(3) ─────────────────────────────────────────────>
            │
            ▼
    demand: ■■■□□□□□□□  (3/10)
            
onNext(A) ──────────────────────────────────────────────>
            │
            ▼
    demand: ■■□□□□□□□□  (2/10)
    buffer: [A]
            
onNext(B) ──────────────────────────────────────────────>
            │
            ▼
    demand: ■□□□□□□□□□  (1/10)
    buffer: [A][B]
```

### Overflow 시나리오

```
Buffer (size: 5):  [A][B][C][D][E]  ← 가득 참!
                         │
                         ▼
새 데이터 'F' 도착 ─────────────────────────────────────>

Strategy에 따라:
  DROP_OLDEST: [B][C][D][E][F]  (A 제거)
  DROP_LATEST: [A][B][C][D][E]  (F 무시)
  ERROR:       onError(BufferOverflow)
```

---

## 5. 테스트

```java
@Test
@DisplayName("DROP_OLDEST 전략 테스트")
void dropOldestStrategy() {
    TestSubscriber<Integer> downstream = new TestSubscriber<>();
    BufferedSubscriber<Integer> buffered = new BufferedSubscriber<>(
        downstream, 3, OverflowStrategy.DROP_OLDEST
    );
    
    ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
    publisher.subscribe(buffered);
    
    // downstream이 천천히 소비
    downstream.request(1);  // 1 받음
    downstream.request(1);  // 2 받음
    downstream.request(1);  // 3, 4, 5 중 버퍼 상황에 따라
    
    // 버퍼가 가득 차면 오래된 것부터 버려짐
}
```

---

## 6. 체크포인트

- [ ] DemandTracker 클래스 구현
- [ ] BufferedSubscriber 구현
- [ ] 3가지 Overflow 전략 구현
- [ ] 테스트 통과

```bash
# OpenCode에서:
/check 3
```

---

## 7. 심화 학습

### 생각해볼 문제

1. 무한 스트림에서 `request(Long.MAX_VALUE)`는 안전할까?
2. 버퍼 크기는 어떻게 정해야 할까?
3. 실제 Reactor에서는 어떤 전략을 사용할까?

---

## 다음 단계

Module 4에서는 map, filter 등 Operator를 구현합니다.

```bash
/learn 4
```
