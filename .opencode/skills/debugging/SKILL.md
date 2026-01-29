---
name: debugging
description: Reactive Streams 디버깅 전략, 로깅, 에러 추적
---

# Debugging Guide for Reactive Streams

## Reactive 디버깅의 어려움

기존 동기 코드와 달리 Reactive 코드는:
- 스택트레이스가 비동기 경계에서 끊김
- 데이터 흐름 추적이 어려움
- 상태 전이 파악이 복잡함

## SignalLogger 활용

### 구현

```java
public class SignalLogger<T> implements Subscriber<T> {
    private final Subscriber<T> actual;
    private final String name;
    
    public SignalLogger(Subscriber<T> actual, String name) {
        this.actual = actual;
        this.name = name;
    }
    
    @Override
    public void onSubscribe(Subscription s) {
        log("onSubscribe(%s)", s);
        actual.onSubscribe(new LoggingSubscription(s));
    }
    
    @Override
    public void onNext(T item) {
        log("onNext(%s)", item);
        actual.onNext(item);
    }
    
    @Override
    public void onError(Throwable t) {
        log("onError(%s)", t.getMessage());
        actual.onError(t);
    }
    
    @Override
    public void onComplete() {
        log("onComplete()");
        actual.onComplete();
    }
    
    private void log(String format, Object... args) {
        System.out.printf("[%s][%s] %s%n", 
            Thread.currentThread().getName(),
            name,
            String.format(format, args)
        );
    }
    
    class LoggingSubscription implements Subscription {
        private final Subscription actual;
        
        @Override
        public void request(long n) {
            log("request(%d)", n);
            actual.request(n);
        }
        
        @Override
        public void cancel() {
            log("cancel()");
            actual.cancel();
        }
    }
}
```

### 사용 예

```java
publisher.subscribe(new SignalLogger<>(subscriber, "MySubscriber"));
```

### 출력 예시

```
[main] MySubscriber: onSubscribe(ArraySubscription@1234)
[main] MySubscriber: request(2)
[main] MySubscriber: onNext(1)
[main] MySubscriber: onNext(2)
[main] MySubscriber: request(3)
[main] MySubscriber: onNext(3)
[main] MySubscriber: onComplete()
```

## 일반적인 버그 패턴

### 1. request 없이 onNext 호출

```java
// Bug: request를 기다리지 않고 바로 발행
@Override
public void subscribe(Subscriber<? super T> s) {
    s.onSubscribe(subscription);
    for (T item : items) {
        s.onNext(item);  // Rule 1.1 위반!
    }
    s.onComplete();
}
```

**증상**: Subscriber가 준비되기 전에 데이터 도착

**해결**:
```java
@Override
public void request(long n) {
    for (int i = 0; i < n && index < items.length; i++) {
        subscriber.onNext(items[index++]);
    }
}
```

### 2. 중복 onComplete/onError

```java
// Bug: 여러 번 완료 시그널
if (hasError) {
    subscriber.onError(error);
}
subscriber.onComplete();  // Rule 1.7 위반!
```

**해결**:
```java
if (hasError) {
    subscriber.onError(error);
    return;  // 조기 반환
}
subscriber.onComplete();
```

### 3. 경쟁 조건

```java
// Bug: 동시성 문제
private long requested = 0;

public void request(long n) {
    requested += n;  // 스레드 안전하지 않음!
    drain();
}
```

**해결**:
```java
private final AtomicLong requested = new AtomicLong(0);

public void request(long n) {
    requested.addAndGet(n);
    drain();
}
```

### 4. cancel 무시

```java
// Bug: cancel 후에도 계속 발행
public void request(long n) {
    for (int i = 0; i < n; i++) {
        subscriber.onNext(items[index++]);  // cancel 체크 안함!
    }
}
```

**해결**:
```java
public void request(long n) {
    for (int i = 0; i < n && !cancelled.get(); i++) {
        subscriber.onNext(items[index++]);
    }
}
```

## 상태 전이 디버깅

### 상태 머신 로깅

```java
public enum State {
    WAITING, SUBSCRIBED, COMPLETED, ERROR, CANCELLED
}

private void transitionTo(State newState) {
    State oldState = state.getAndSet(newState);
    log("State: %s -> %s", oldState, newState);
    
    // 잘못된 전이 감지
    if (oldState == State.COMPLETED || oldState == State.ERROR) {
        log("WARNING: Transition from terminal state!");
    }
}
```

## 디버깅 체크리스트

1. **시그널 순서 확인**
   - onSubscribe가 먼저 호출되었는가?
   - onNext가 request 이후에 호출되었는가?
   - onComplete/onError가 마지막인가?

2. **동시성 확인**
   - 상태 변수가 Atomic인가?
   - 경쟁 조건이 있는가?

3. **cancel 처리 확인**
   - cancel 후 시그널이 중지되는가?
   - 리소스가 정리되는가?

4. **에러 전파 확인**
   - 예외가 적절히 onError로 전달되는가?
   - onError 후 다른 시그널이 없는가?

## 관련 스킬

- `reactive-spec`: 규약 상세
- `testing`: 테스트 전략
