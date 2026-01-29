---
name: operator-pattern
description: Reactive Operator 구현 패턴, 체이닝, 변환
---

# Operator Pattern Guide

## Operator란?

Operator는 Publisher를 입력으로 받아 새로운 Publisher를 반환하는 함수입니다.
데이터 스트림을 변환, 필터링, 조합하는 역할을 합니다.

```
Source         map(x -> x*2)    filter(x > 5)    Terminal
Publisher ──────────────────────────────────────> Subscriber
   [1,2,3]        [2,4,6]          [6]              [6]
```

## 기본 구조

### Operator의 이중 역할

Operator는 **Subscriber이자 Publisher**입니다:

```
upstream                    Operator                    downstream
Publisher ────────────────> [Subscriber | Publisher] ────────────> Subscriber
                                  │
                           데이터 변환/필터링
```

### 기본 구현 패턴

```java
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
        private Subscription upstream;
        
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(s);  // Subscription 전달
        }
        
        @Override
        public void onNext(T item) {
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

## 주요 Operator 구현

### 1. Map (변환)

```java
// 사용
Publisher<Integer> doubled = new MapOperator<>(source, x -> x * 2);

// 체이닝을 위한 확장 메서드 (Base Publisher에 추가)
public <R> Publisher<R> map(Function<T, R> mapper) {
    return new MapOperator<>(this, mapper);
}
```

### 2. Filter (필터링)

```java
public class FilterOperator<T> implements Publisher<T> {
    private final Publisher<T> upstream;
    private final Predicate<T> predicate;
    
    static class FilterSubscriber<T> implements Subscriber<T>, Subscription {
        private final Subscriber<? super T> downstream;
        private final Predicate<T> predicate;
        private Subscription upstream;
        
        @Override
        public void onNext(T item) {
            if (predicate.test(item)) {
                downstream.onNext(item);
            } else {
                // 필터링된 경우 추가 요청
                upstream.request(1);
            }
        }
        
        // request를 래핑해야 함
        @Override
        public void request(long n) {
            upstream.request(n);
        }
    }
}
```

**주의**: Filter는 request 처리가 복잡합니다. 필터링된 요소만큼 추가 request가 필요합니다.

### 3. Take (개수 제한)

```java
public class TakeOperator<T> implements Publisher<T> {
    private final Publisher<T> upstream;
    private final long limit;
    
    static class TakeSubscriber<T> implements Subscriber<T> {
        private final long limit;
        private long count = 0;
        private Subscription upstream;
        private boolean done = false;
        
        @Override
        public void onNext(T item) {
            if (done) return;
            
            count++;
            downstream.onNext(item);
            
            if (count >= limit) {
                done = true;
                upstream.cancel();
                downstream.onComplete();
            }
        }
    }
}
```

### 4. FlatMap (평탄화)

FlatMap은 가장 복잡한 Operator입니다:

```java
// 각 요소를 Publisher로 변환하고 결과를 평탄화
Publisher<Integer> result = source.flatMap(x -> 
    new ArrayPublisher<>(x, x*2, x*3)
);

// [1, 2] → [[1,2,3], [2,4,6]] → [1,2,3,2,4,6]
```

```java
public class FlatMapOperator<T, R> implements Publisher<R> {
    private final Publisher<T> upstream;
    private final Function<T, Publisher<R>> mapper;
    
    static class FlatMapSubscriber<T, R> implements Subscriber<T> {
        private final Function<T, Publisher<R>> mapper;
        private final Subscriber<? super R> downstream;
        private final List<InnerSubscriber> inners = new CopyOnWriteArrayList<>();
        private volatile boolean done = false;
        
        @Override
        public void onNext(T item) {
            Publisher<R> inner = mapper.apply(item);
            InnerSubscriber innerSubscriber = new InnerSubscriber();
            inners.add(innerSubscriber);
            inner.subscribe(innerSubscriber);
        }
        
        class InnerSubscriber implements Subscriber<R> {
            @Override
            public void onNext(R item) {
                downstream.onNext(item);
            }
            
            @Override
            public void onComplete() {
                inners.remove(this);
                checkComplete();
            }
        }
        
        void checkComplete() {
            if (done && inners.isEmpty()) {
                downstream.onComplete();
            }
        }
    }
}
```

## Operator 체이닝

### Fluent API 구현

```java
public abstract class BasePublisher<T> implements Publisher<T> {
    
    public <R> BasePublisher<R> map(Function<T, R> mapper) {
        return new MapOperator<>(this, mapper);
    }
    
    public BasePublisher<T> filter(Predicate<T> predicate) {
        return new FilterOperator<>(this, predicate);
    }
    
    public BasePublisher<T> take(long n) {
        return new TakeOperator<>(this, n);
    }
}
```

### 사용 예

```java
new ArrayPublisher<>(1, 2, 3, 4, 5)
    .map(x -> x * 2)           // [2, 4, 6, 8, 10]
    .filter(x -> x > 5)        // [6, 8, 10]
    .take(2)                   // [6, 8]
    .subscribe(subscriber);
```

## Backpressure 전파

Operator는 Backpressure를 올바르게 전파해야 합니다:

```
downstream.request(n)
        │
        ↓
   Operator (map, filter, etc.)
        │
        ↓
upstream.request(n)  // 보통 그대로 전달
```

### Filter의 특수 케이스

```java
// downstream이 request(1) 했는데
// 첫 번째 요소가 필터링되면?
// → upstream에 request(1) 추가로 해야 함

@Override
public void onNext(T item) {
    if (predicate.test(item)) {
        downstream.onNext(item);
    } else {
        upstream.request(1);  // 필터링된 만큼 추가 요청
    }
}
```

## Marble Diagram

```
Source:   ──1──2──3──4──5──|
              │
           map(x*2)
              │
          ──2──4──6──8──10──|
              │
         filter(x>5)
              │
          ────────6──8──10──|
              │
           take(2)
              │
          ────────6──8──|
```

## 관련 스킬

- `reactive-spec`: 규약 상세
- `backpressure`: Backpressure 전략
