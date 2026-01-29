---
name: reactive-spec
description: Reactive Streams 전체 규약 (39개 규칙) 상세 설명
---

# Reactive Streams Specification

Reactive Streams는 비동기 스트림 처리를 위한 표준 규약입니다. 
총 39개의 규칙으로 Publisher, Subscriber, Subscription, Processor의 동작을 정의합니다.

## 1. Publisher 규칙 (11개)

### Rule 1.01
> Publisher.subscribe가 호출되면 반드시 Subscriber.onSubscribe를 호출해야 한다.

```java
@Override
public void subscribe(Subscriber<? super T> subscriber) {
    // 반드시 onSubscribe를 먼저 호출
    subscriber.onSubscribe(new MySubscription(subscriber));
}
```

### Rule 1.02
> Publisher는 요청된 것보다 적은 onNext를 호출할 수 있지만, 
> 더 많은 onNext를 호출해서는 안 된다.

```java
// request(5)가 호출되면
// onNext는 0~5회 호출 가능, 6회 이상은 규약 위반
```

### Rule 1.03
> onSubscribe, onNext, onError, onComplete는 시그널 순서를 지켜야 한다.
> onSubscribe → onNext* → (onError | onComplete)

```
올바른 순서:
onSubscribe → onNext → onNext → onComplete

잘못된 순서:
onNext → onSubscribe  (위반!)
onComplete → onNext   (위반!)
```

### Rule 1.04
> Publisher가 실패하면 반드시 onError를 시그널해야 한다.

### Rule 1.05
> Publisher가 성공적으로 종료되면 반드시 onComplete를 시그널해야 한다.

### Rule 1.06
> onError나 onComplete가 시그널되면 Subscription은 취소된 것으로 간주한다.

### Rule 1.07
> 터미널 시그널(onError/onComplete) 후에는 어떤 시그널도 발생하면 안 된다.

```java
// 잘못된 예
subscriber.onComplete();
subscriber.onNext(item);  // 규약 위반!
```

### Rule 1.08
> Subscription이 취소되면 추가 시그널 발생을 중지해야 한다.

### Rule 1.09
> subscribe는 null이 아닌 Subscriber에게 호출되어야 한다. 
> null이면 NullPointerException을 던져야 한다.

### Rule 1.10
> subscribe는 여러 번 호출될 수 있으며, 
> 매번 다른 Subscriber에게 전달될 수 있다.

### Rule 1.11
> Publisher는 여러 Subscriber를 지원할 수 있지만, 
> 각 Subscription은 단일 Subscriber와 연결된다.

## 2. Subscriber 규칙 (13개)

### Rule 2.01
> Subscriber는 onSubscribe를 통해 Subscription을 받은 후에야 
> request를 통해 demand를 시그널할 수 있다.

### Rule 2.02
> Subscriber.onComplete나 Subscriber.onError가 Subscription이나 Publisher에게 
> 예외를 던지면 안 된다.

### Rule 2.03
> Subscriber.onComplete나 Subscriber.onError가 호출되면 
> Subscription은 취소된 것으로 간주한다.

### Rule 2.04
> Subscriber는 Subscription 참조가 더 이상 필요없으면 폐기해야 한다.

### Rule 2.05
> Subscriber는 onSubscribe 이후에 request를 동기적으로 호출할 수 있다.

### Rule 2.06
> Subscriber.onSubscribe는 주어진 Subscription에 대해 최대 한 번만 호출되어야 한다.

### Rule 2.07
> Subscriber는 Subscription.request를 호출해서 demand를 시그널해야 한다.

### Rule 2.08
> Subscriber는 Subscription.cancel을 호출해서 구독을 취소할 수 있다.

### Rule 2.09
> Subscriber는 onNext 시그널 처리 전에 request를 호출하는 것이 권장된다.

### Rule 2.10
> Subscriber.onSubscribe는 해당 Subscriber에 대해 최대 한 번만 호출되어야 한다.

### Rule 2.11
> Subscriber는 onNext에서 request를 호출하여 재귀적으로 demand를 시그널할 수 있다.

### Rule 2.12
> Subscriber.onSubscribe는 반드시 subscription.cancel()이나 subscription.request()를 
> 정상적으로 반환하기 전에 호출해야 한다.

### Rule 2.13
> Subscriber.onSubscribe가 여러 번 호출되면 (이미 활성 Subscription이 있다면) 
> 새 Subscription을 cancel해야 한다.

```java
@Override
public void onSubscribe(Subscription s) {
    if (this.subscription != null) {
        s.cancel();  // 기존 구독이 있으면 새 것은 취소
        return;
    }
    this.subscription = s;
}
```

## 3. Subscription 규칙 (17개)

### Rule 3.01
> request와 cancel은 Subscriber에 의해서만 호출되어야 한다.

### Rule 3.02
> request는 Subscriber 컨텍스트 내에서 호출되어야 한다.

### Rule 3.03
> request는 Publisher에게 지정된 수의 onNext 호출을 요청한다.

### Rule 3.04
> request는 Publisher와 Subscriber 사이의 비동기 시그널을 허용한다.

### Rule 3.05
> request는 반드시 재진입(reentrant)을 허용해야 한다.

### Rule 3.06
> cancel 후에는 추가 request 호출이 효과가 없어야 한다.

### Rule 3.07
> cancel 후에는 추가 cancel 호출이 효과가 없어야 한다.

### Rule 3.08
> Subscription이 취소되지 않은 동안, request(n)은 n > 0이어야 한다.
> n ≤ 0이면 onError(IllegalArgumentException)을 시그널해야 한다.

```java
@Override
public void request(long n) {
    if (n <= 0) {
        subscriber.onError(
            new IllegalArgumentException("Rule 3.9: n must be > 0")
        );
        return;
    }
    // 정상 처리
}
```

### Rule 3.09
> Subscription이 취소되지 않은 동안, cancel은 Publisher가 
> 궁극적으로 시그널 발생을 중지하도록 요청해야 한다.

### Rule 3.10
> Subscription이 취소되지 않은 동안, cancel은 Publisher가 
> 궁극적으로 해당 Subscriber 참조를 삭제하도록 요청해야 한다.

### Rule 3.11
> Subscription이 취소되지 않은 동안, cancel은 request의 처리나 
> 취소 후 onNext 시그널의 전달 중에 호출될 수 있다.

### Rule 3.12
> cancel은 반환하기 전에 요청된 효과를 수행해야 한다.

### Rule 3.13
> cancel은 멱등해야 한다 (여러 번 호출해도 같은 효과).

### Rule 3.14
> cancel은 반드시 스레드 안전해야 한다.

### Rule 3.15
> cancel은 반환하기 전에 Subscription을 동기적으로 취소해야 한다.

### Rule 3.16
> request는 반드시 스레드 안전해야 한다.

### Rule 3.17
> request는 반환하기 전에 동기적으로 demand를 추가해야 한다.

## 4. Processor 규칙 (2개)

### Rule 4.01
> Processor는 Publisher와 Subscriber 계약을 모두 준수해야 한다.

### Rule 4.02
> Processor는 onError 시그널을 복구할 수 있다. 
> 복구하면 Subscription이 취소된 것으로 간주하고, 
> 그렇지 않으면 Subscriber에게 즉시 onError를 전파해야 한다.

## 규약 요약 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                    Signal Flow                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Subscriber                         Publisher              │
│       │                                  │                  │
│       │──────── subscribe ──────────────>│                  │
│       │<─────── onSubscribe ─────────────│                  │
│       │                                  │                  │
│       │──────── request(n) ─────────────>│                  │
│       │<─────── onNext (0..n times) ─────│                  │
│       │                                  │                  │
│       │<─────── onComplete ──────────────│  Terminal        │
│       │    OR   onError ─────────────────│  (one of)        │
│       │                                  │                  │
│       │──────── cancel ─────────────────>│  (optional)      │
│       │                                  │                  │
└─────────────────────────────────────────────────────────────┘
```

## 관련 스킬

- `backpressure`: Backpressure 상세 가이드
- `operator-pattern`: Operator 구현 패턴
- `testing`: TCK 테스트 가이드
