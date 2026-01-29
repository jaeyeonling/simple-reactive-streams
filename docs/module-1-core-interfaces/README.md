# Module 1: 핵심 인터페이스

> ⏱️ 예상 시간: 1시간 | ★★☆☆☆ 난이도

## 학습 목표

- Publisher, Subscriber, Subscription, Processor 인터페이스의 역할 이해
- 각 인터페이스의 메서드와 시그널 이해
- 직접 인터페이스 정의하기

---

## 1. 문제 제시

### 시나리오

당신은 실시간 주식 시세 시스템을 개발하고 있습니다.

```
┌──────────────────────────────────────────────────────────┐
│  주식 시세 서버                                           │
│  - 초당 수천 건의 시세 데이터 생성                          │
│  - 여러 클라이언트가 구독                                  │
│  - 클라이언트마다 처리 속도가 다름                          │
└──────────────────────────────────────────────────────────┘
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
    빠른 클라이언트  보통       느린 클라이언트
    (1000/초)      (100/초)    (10/초)
```

### 문제

1. 생산자(서버)와 소비자(클라이언트) 사이의 **계약**을 어떻게 정의할까?
2. 소비자가 **준비되지 않았을 때** 데이터를 어떻게 처리할까?
3. **에러**가 발생하면 어떻게 전달할까?
4. 데이터 전송이 **끝났음**을 어떻게 알릴까?
5. 소비자가 **더 이상 원하지 않으면** 어떻게 할까?

### 도전

위 문제를 해결할 수 있는 **인터페이스들을 직접 설계**해보세요!

---

## 2. 개념 설명

### Publisher (발행자)

데이터 스트림을 발행하는 주체입니다.

```
비유: 신문사 (Publisher)
- 구독 신청을 받음 (subscribe)
- 구독자에게 신문을 배달 (onNext)
- 폐간 시 알림 (onComplete)
- 문제 발생 시 알림 (onError)
```

```java
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}
```

### Subscriber (구독자)

데이터를 수신하고 처리하는 주체입니다.

```
비유: 신문 구독자 (Subscriber)
- 구독 계약서 받음 (onSubscribe)
- 신문 받음 (onNext)
- 폐간 알림 받음 (onComplete)
- 문제 알림 받음 (onError)
```

```java
public interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T item);
    void onError(Throwable throwable);
    void onComplete();
}
```

### Subscription (구독)

Publisher와 Subscriber 사이의 계약입니다.

```
비유: 구독 계약서 (Subscription)
- "이번 달은 5부만 배달해주세요" (request)
- "구독 취소합니다" (cancel)
```

```java
public interface Subscription {
    void request(long n);
    void cancel();
}
```

### Processor (처리기)

Publisher이자 Subscriber입니다. 데이터를 받아서 변환 후 다시 발행합니다.

```
비유: 번역 서비스 (Processor)
- 영어 신문을 구독 (Subscriber)
- 한국어로 번역
- 한국어 신문 발행 (Publisher)
```

```java
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

---

## 3. 시그널 흐름

### 정상 흐름

```
Subscriber                         Publisher
    │                                  │
    │──────── subscribe ──────────────>│  ① 구독 요청
    │                                  │
    │<─────── onSubscribe(s) ──────────│  ② 구독 수락
    │                                  │
    │──────── s.request(3) ───────────>│  ③ 3개 요청
    │                                  │
    │<─────── onNext(1) ───────────────│  ④ 데이터 1
    │<─────── onNext(2) ───────────────│  ⑤ 데이터 2
    │<─────── onNext(3) ───────────────│  ⑥ 데이터 3
    │                                  │
    │──────── s.request(2) ───────────>│  ⑦ 2개 더 요청
    │                                  │
    │<─────── onNext(4) ───────────────│  ⑧ 데이터 4
    │<─────── onNext(5) ───────────────│  ⑨ 데이터 5
    │                                  │
    │<─────── onComplete() ────────────│  ⑩ 완료
```

### 에러 흐름

```
Subscriber                         Publisher
    │                                  │
    │<─────── onSubscribe(s) ──────────│
    │──────── s.request(10) ──────────>│
    │<─────── onNext(1) ───────────────│
    │<─────── onNext(2) ───────────────│
    │                                  │
    │<─────── onError(e) ──────────────│  에러 발생!
    │                                  │
    │          (더 이상 시그널 없음)      │
```

### 취소 흐름

```
Subscriber                         Publisher
    │                                  │
    │<─────── onSubscribe(s) ──────────│
    │──────── s.request(10) ──────────>│
    │<─────── onNext(1) ───────────────│
    │<─────── onNext(2) ───────────────│
    │                                  │
    │──────── s.cancel() ─────────────>│  구독 취소!
    │                                  │
    │          (더 이상 시그널 없음)      │
```

---

## 4. 구현 가이드

### Step 1: Publisher 인터페이스

`src/main/java/io/simplereactive/core/Publisher.java` 파일을 생성하세요.

```java
package io.simplereactive.core;

/**
 * 데이터 스트림의 발행자.
 * 
 * Subscriber가 subscribe()를 호출하면 데이터 발행을 시작합니다.
 * 
 * @param <T> 발행할 데이터의 타입
 */
public interface Publisher<T> {
    
    /**
     * Subscriber의 구독을 받아들입니다.
     * 
     * 호출 시 반드시 onSubscribe를 호출해야 합니다. (Rule 1.1)
     * 
     * @param subscriber 구독할 Subscriber (null 불가)
     * @throws NullPointerException subscriber가 null인 경우 (Rule 1.9)
     */
    void subscribe(Subscriber<? super T> subscriber);
}
```

### Step 2: Subscriber 인터페이스

`src/main/java/io/simplereactive/core/Subscriber.java` 파일을 생성하세요.

```java
package io.simplereactive.core;

/**
 * 데이터 스트림의 구독자.
 * 
 * Publisher로부터 데이터를 수신하고 처리합니다.
 * 
 * @param <T> 수신할 데이터의 타입
 */
public interface Subscriber<T> {
    
    /**
     * 구독이 시작될 때 호출됩니다.
     * 
     * Subscription을 통해 데이터를 요청(request)하거나 
     * 구독을 취소(cancel)할 수 있습니다.
     * 
     * @param subscription Publisher와의 구독 관계
     */
    void onSubscribe(Subscription subscription);
    
    /**
     * 데이터가 도착할 때마다 호출됩니다.
     * 
     * request(n)으로 요청한 개수만큼만 호출됩니다.
     * 
     * @param item 수신한 데이터 (null 불가)
     */
    void onNext(T item);
    
    /**
     * 에러 발생 시 호출됩니다.
     * 
     * 이후에는 어떤 시그널도 오지 않습니다.
     * 
     * @param throwable 발생한 에러
     */
    void onError(Throwable throwable);
    
    /**
     * 모든 데이터 전송이 완료되었을 때 호출됩니다.
     * 
     * 이후에는 어떤 시그널도 오지 않습니다.
     */
    void onComplete();
}
```

### Step 3: Subscription 인터페이스

`src/main/java/io/simplereactive/core/Subscription.java` 파일을 생성하세요.

```java
package io.simplereactive.core;

/**
 * Publisher와 Subscriber 간의 구독 관계.
 * 
 * Subscriber가 데이터를 요청하거나 구독을 취소하는 데 사용합니다.
 */
public interface Subscription {
    
    /**
     * Publisher에게 n개의 데이터를 요청합니다.
     * 
     * n <= 0이면 onError(IllegalArgumentException)가 호출됩니다. (Rule 3.9)
     * 
     * @param n 요청할 데이터 개수 (양수여야 함)
     */
    void request(long n);
    
    /**
     * 구독을 취소합니다.
     * 
     * 이후에는 더 이상 데이터를 받지 않습니다.
     * 여러 번 호출해도 안전합니다 (멱등).
     */
    void cancel();
}
```

### Step 4: Processor 인터페이스

`src/main/java/io/simplereactive/core/Processor.java` 파일을 생성하세요.

```java
package io.simplereactive.core;

/**
 * Publisher이자 Subscriber인 처리기.
 * 
 * 데이터를 수신하여 변환한 후 다시 발행합니다.
 * 
 * @param <T> 입력 데이터 타입
 * @param <R> 출력 데이터 타입
 */
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

---

## 5. 검증

### 테스트 실행

```bash
./gradlew test --tests "*CoreInterfaceTest*"
```

### 체크포인트

- [ ] Publisher 인터페이스 정의 완료
- [ ] Subscriber 인터페이스 정의 완료
- [ ] Subscription 인터페이스 정의 완료
- [ ] Processor 인터페이스 정의 완료
- [ ] 각 메서드에 Javadoc 작성
- [ ] 빌드 성공 (`./gradlew build`)

### 자가 점검

```bash
# OpenCode에서:
/check 1
```

---

## 6. 심화 학습

### 실제 구현체와 비교

Java 9+에는 `java.util.concurrent.Flow`에 동일한 인터페이스가 있습니다:

```java
// 우리가 만든 것
io.simplereactive.core.Publisher

// Java 표준
java.util.concurrent.Flow.Publisher

// Reactive Streams 표준
org.reactivestreams.Publisher
```

### 생각해볼 질문

1. 왜 `request(long n)`의 타입이 `long`일까? `int`면 안 될까?
2. 왜 Processor는 별도 메서드 없이 두 인터페이스를 상속만 할까?
3. `onComplete()`와 `onError()`는 왜 둘 중 하나만 호출될까?

---

## 다음 단계

Module 2에서는 첫 번째 Publisher인 ArrayPublisher를 구현합니다.

```bash
# OpenCode에서:
/learn 2
```
