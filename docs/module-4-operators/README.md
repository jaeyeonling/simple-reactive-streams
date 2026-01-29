# Module 4: Operators 구현

> ⏱️ 예상 시간: 3시간 | ★★★★☆ 난이도

## 학습 목표

- Operator 패턴 이해
- map, filter, take Operator 구현
- Operator 체이닝 구현

---

## 1. 문제 제시

### 시나리오

숫자 스트림 `[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]`에서:
1. 각 숫자를 2배로 만들고 (map)
2. 10보다 큰 것만 필터링하고 (filter)
3. 처음 3개만 가져오기 (take)

```java
// 원하는 코드
publisher
    .map(x -> x * 2)      // [2,4,6,8,10,12,14,16,18,20]
    .filter(x -> x > 10)  // [12,14,16,18,20]
    .take(3)              // [12,14,16]
    .subscribe(subscriber);
```

---

## 2. 개념 설명

### Operator란?

Operator는 **Publisher를 입력받아 새로운 Publisher를 반환**합니다.

```
Source         Operator        Operator        Terminal
Publisher ────────────────────────────────────> Subscriber
   │              │              │                 │
   │   map(x*2)   │  filter(>5)  │    take(2)     │
   │              │              │                 │
  [1,2,3]      [2,4,6]         [6]              [6]
```

### Operator의 이중 역할

Operator는 **Subscriber이자 Publisher**입니다:

```
┌────────────────────────────────────────────────────────┐
│                      Operator                          │
├────────────────────────────────────────────────────────┤
│                                                        │
│   upstream                      downstream             │
│   Publisher ──> [Subscriber] [Publisher] ──> Subscriber│
│                      │              │                  │
│                      └──── 변환 ────┘                  │
│                                                        │
└────────────────────────────────────────────────────────┘
```

---

## 3. 구현 가이드

### Step 1: MapOperator

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import java.util.function.Function;

/**
 * 각 요소를 변환하는 Operator.
 */
public class MapOperator<T, R> implements Publisher<R> {
    
    private final Publisher<T> upstream;
    private final Function<T, R> mapper;
    
    public MapOperator(Publisher<T> upstream, Function<T, R> mapper) {
        this.upstream = upstream;
        this.mapper = mapper;
    }
    
    @Override
    public void subscribe(Subscriber<? super R> downstream) {
        upstream.subscribe(new MapSubscriber<>(downstream, mapper));
    }
    
    static class MapSubscriber<T, R> implements Subscriber<T> {
        private final Subscriber<? super R> downstream;
        private final Function<T, R> mapper;
        
        MapSubscriber(Subscriber<? super R> downstream, Function<T, R> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            // Subscription을 그대로 전달
            downstream.onSubscribe(s);
        }
        
        @Override
        public void onNext(T item) {
            // 변환 후 전달
            R mapped = mapper.apply(item);
            downstream.onNext(mapped);
        }
        
        @Override
        public void onError(Throwable t) {
            downstream.onError(t);
        }
        
        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }
}
```

### Step 2: FilterOperator

Filter는 조금 복잡합니다. 필터링된 요소는 다시 요청해야 합니다:

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import java.util.function.Predicate;

/**
 * 조건에 맞는 요소만 통과시키는 Operator.
 */
public class FilterOperator<T> implements Publisher<T> {
    
    private final Publisher<T> upstream;
    private final Predicate<T> predicate;
    
    public FilterOperator(Publisher<T> upstream, Predicate<T> predicate) {
        this.upstream = upstream;
        this.predicate = predicate;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        upstream.subscribe(new FilterSubscriber<>(downstream, predicate));
    }
    
    static class FilterSubscriber<T> implements Subscriber<T>, Subscription {
        private final Subscriber<? super T> downstream;
        private final Predicate<T> predicate;
        private Subscription upstream;
        
        FilterSubscriber(Subscriber<? super T> downstream, Predicate<T> predicate) {
            this.downstream = downstream;
            this.predicate = predicate;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            // 우리가 만든 Subscription을 전달
            downstream.onSubscribe(this);
        }
        
        @Override
        public void onNext(T item) {
            if (predicate.test(item)) {
                downstream.onNext(item);
            } else {
                // 필터링된 만큼 추가 요청!
                upstream.request(1);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            downstream.onError(t);
        }
        
        @Override
        public void onComplete() {
            downstream.onComplete();
        }
        
        // Subscription 구현
        @Override
        public void request(long n) {
            upstream.request(n);
        }
        
        @Override
        public void cancel() {
            upstream.cancel();
        }
    }
}
```

### Step 3: TakeOperator

지정된 개수만 받고 취소합니다:

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 처음 n개만 통과시키는 Operator.
 */
public class TakeOperator<T> implements Publisher<T> {
    
    private final Publisher<T> upstream;
    private final long limit;
    
    public TakeOperator(Publisher<T> upstream, long limit) {
        this.upstream = upstream;
        this.limit = limit;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        upstream.subscribe(new TakeSubscriber<>(downstream, limit));
    }
    
    static class TakeSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> downstream;
        private final long limit;
        private final AtomicLong count = new AtomicLong(0);
        private Subscription upstream;
        private volatile boolean done = false;
        
        TakeSubscriber(Subscriber<? super T> downstream, long limit) {
            this.downstream = downstream;
            this.limit = limit;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(s);
        }
        
        @Override
        public void onNext(T item) {
            if (done) return;
            
            long c = count.incrementAndGet();
            downstream.onNext(item);
            
            if (c >= limit) {
                done = true;
                upstream.cancel();
                downstream.onComplete();
            }
        }
        
        @Override
        public void onError(Throwable t) {
            if (!done) {
                downstream.onError(t);
            }
        }
        
        @Override
        public void onComplete() {
            if (!done) {
                downstream.onComplete();
            }
        }
    }
}
```

### Step 4: Fluent API를 위한 BasePublisher

```java
package io.simplereactive.core;

import io.simplereactive.operator.*;
import java.util.function.*;

/**
 * Operator 체이닝을 위한 기본 Publisher.
 */
public abstract class BasePublisher<T> implements Publisher<T> {
    
    public <R> BasePublisher<R> map(Function<T, R> mapper) {
        Publisher<T> self = this;
        return new BasePublisher<R>() {
            @Override
            public void subscribe(Subscriber<? super R> subscriber) {
                new MapOperator<>(self, mapper).subscribe(subscriber);
            }
        };
    }
    
    public BasePublisher<T> filter(Predicate<T> predicate) {
        Publisher<T> self = this;
        return new BasePublisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                new FilterOperator<>(self, predicate).subscribe(subscriber);
            }
        };
    }
    
    public BasePublisher<T> take(long n) {
        Publisher<T> self = this;
        return new BasePublisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                new TakeOperator<>(self, n).subscribe(subscriber);
            }
        };
    }
}
```

---

## 4. Marble Diagram

```
Source:   ──1──2──3──4──5──6──7──8──9──10──|
              │
           map(x*2)
              │
          ──2──4──6──8──10─12─14─16─18─20──|
              │
         filter(>10)
              │
          ─────────────12─14─16─18─20──|
              │
           take(3)
              │
          ─────────────12─14─16──|
```

---

## 5. 테스트

```java
@Test
@DisplayName("map -> filter -> take 체이닝")
void operatorChaining() {
    ArrayPublisher<Integer> source = new ArrayPublisher<>(1,2,3,4,5,6,7,8,9,10);
    TestSubscriber<Integer> subscriber = new TestSubscriber<>();
    
    source
        .map(x -> x * 2)
        .filter(x -> x > 10)
        .take(3)
        .subscribe(subscriber);
    
    subscriber.request(Long.MAX_VALUE);
    
    assertThat(subscriber.received).containsExactly(12, 14, 16);
    assertThat(subscriber.completed).isTrue();
}
```

---

## 6. 체크포인트

- [ ] MapOperator 구현
- [ ] FilterOperator 구현 (추가 request 포함)
- [ ] TakeOperator 구현 (cancel 포함)
- [ ] BasePublisher로 체이닝 지원
- [ ] 테스트 통과

```bash
/check 4
```

---

## 다음 단계

Module 5에서는 에러 처리를 학습합니다.

```bash
/learn 5
```
