# Module 7: Hot vs Cold Publisher

> ⏱️ 예상 시간: 1.5시간 | ★★★☆☆ 난이도

## 학습 목표

- Cold Publisher와 Hot Publisher의 차이 이해
- Hot Publisher 구현
- 실제 사용 사례 학습

---

## 1. 개념 설명

### Cold Publisher

**구독할 때마다 처음부터 새로 발행**합니다.

```
비유: VOD (주문형 비디오)
- 언제 시작하든 영화의 처음부터
- 각 시청자가 독립적인 스트림

구독 시점:
Subscriber A ─────┬─────────────────────────>
                  │ [1, 2, 3, 4, 5]
                  
Subscriber B ──────────────┬────────────────>
          (나중에 구독)     │ [1, 2, 3, 4, 5]  ← 처음부터!
```

### Hot Publisher

**구독 시점과 관계없이 계속 발행**, 늦게 구독하면 이전 데이터는 놓침.

```
비유: 실시간 방송 (TV)
- 방송은 계속 진행
- 늦게 켜면 이전 내용은 놓침

시간 ──────────────────────────────────────>
데이터     1     2     3     4     5
           ↓     ↓     ↓     ↓     ↓
Subscriber A ────[1]──[2]──[3]──[4]──[5]──
                       ↑
Subscriber B ──────────[2]──[3]──[4]──[5]──  ← 2부터!
                             ↑
Subscriber C ────────────────[3]──[4]──[5]──  ← 3부터!
```

### 비교

| 특성 | Cold Publisher | Hot Publisher |
|------|---------------|---------------|
| 데이터 생성 | 구독 시 시작 | 구독과 무관하게 진행 |
| 구독자별 데이터 | 독립적 (각자 처음부터) | 공유 (같은 데이터) |
| 예시 | DB 쿼리, HTTP 요청 | 주식 시세, 센서 데이터 |

---

## 2. 구현 가이드

### Cold Publisher (이미 구현함)

```java
// ArrayPublisher는 Cold Publisher
// 구독할 때마다 index가 0부터 시작
ArrayPublisher<Integer> cold = new ArrayPublisher<>(1, 2, 3);
```

### Hot Publisher 구현

```java
package io.simplereactive.publisher;

import io.simplereactive.core.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hot Publisher - 여러 Subscriber에게 같은 데이터를 발행.
 */
public class SimpleHotPublisher<T> implements Publisher<T> {
    
    private final List<Subscriber<? super T>> subscribers = 
        new CopyOnWriteArrayList<>();
    private volatile boolean completed = false;
    private volatile Throwable error = null;
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if (completed) {
            // 이미 완료된 경우
            subscriber.onSubscribe(new EmptySubscription());
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
            return;
        }
        
        subscribers.add(subscriber);
        subscriber.onSubscribe(new HotSubscription(subscriber));
    }
    
    /**
     * 외부에서 데이터를 발행합니다.
     */
    public void emit(T item) {
        if (completed) return;
        
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onNext(item);
        }
    }
    
    /**
     * 완료 시그널을 보냅니다.
     */
    public void complete() {
        if (completed) return;
        completed = true;
        
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onComplete();
        }
        subscribers.clear();
    }
    
    /**
     * 에러 시그널을 보냅니다.
     */
    public void error(Throwable t) {
        if (completed) return;
        completed = true;
        error = t;
        
        for (Subscriber<? super T> subscriber : subscribers) {
            subscriber.onError(t);
        }
        subscribers.clear();
    }
    
    class HotSubscription implements Subscription {
        private final Subscriber<? super T> subscriber;
        
        HotSubscription(Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }
        
        @Override
        public void request(long n) {
            // Hot Publisher는 demand를 무시
            // (발행 속도를 제어하지 않음)
        }
        
        @Override
        public void cancel() {
            subscribers.remove(subscriber);
        }
    }
    
    static class EmptySubscription implements Subscription {
        @Override
        public void request(long n) {}
        
        @Override
        public void cancel() {}
    }
}
```

---

## 3. 사용 예시

```java
// Hot Publisher 생성
SimpleHotPublisher<String> hot = new SimpleHotPublisher<>();

// 첫 번째 구독자
hot.subscribe(new PrintSubscriber("A"));

// 데이터 발행
hot.emit("Hello");   // A만 받음
hot.emit("World");   // A만 받음

// 두 번째 구독자 (늦게 구독)
hot.subscribe(new PrintSubscriber("B"));

hot.emit("Foo");     // A, B 모두 받음
hot.emit("Bar");     // A, B 모두 받음

hot.complete();

// 출력:
// A: Hello
// A: World
// A: Foo
// B: Foo
// A: Bar
// B: Bar
```

---

## 4. 실제 사용 사례

### Cold Publisher 사용

```java
// HTTP 요청 - 각 구독자마다 새 요청
Publisher<Response> httpRequest = createHttpPublisher("/api/users");

// DB 쿼리 - 각 구독자마다 새 쿼리
Publisher<User> dbQuery = createDbPublisher("SELECT * FROM users");
```

### Hot Publisher 사용

```java
// 주식 시세 - 모든 구독자가 같은 시세를 받음
HotPublisher<StockPrice> stockPrice = createStockPricePublisher();

// 센서 데이터 - 실시간 측정값
HotPublisher<SensorData> sensor = createSensorPublisher();

// 사용자 이벤트 - 클릭, 키보드 입력 등
HotPublisher<Event> userEvents = createUserEventPublisher();
```

---

## 5. 체크포인트

- [ ] Cold vs Hot 차이점 이해
- [ ] SimpleHotPublisher 구현
- [ ] 여러 구독자 테스트
- [ ] 늦은 구독자 테스트

```bash
/check 7
```

---

## 다음 단계

Module 8에서는 배운 내용을 실전에 적용합니다!

```bash
/learn 8
```
