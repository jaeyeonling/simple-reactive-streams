---
name: code-quality
description: 코드 품질 가이드, 클린 코드 원칙, 리팩토링 패턴
---

# Code Quality Guide

## 클린 코드 원칙

### 1. 의미 있는 이름

```java
// Bad
int d = getCurrentDate();
List<User> arr = users.stream().filter(u -> u.a > 18).toList();

// Good
int currentDate = getCurrentDate();
List<User> adultUsers = users.stream().filter(user -> user.age > 18).toList();
```

### 2. 함수는 작게, 한 가지 일만

```java
// Bad: 여러 일을 하는 함수
public void processUser(User user) {
    // validation
    if (user.getEmail() == null) {
        throw new IllegalArgumentException("Email required");
    }
    // normalization
    user.setEmail(user.getEmail().toLowerCase());
    // save
    userRepository.save(user);
    // notification
    emailService.sendWelcome(user);
}

// Good: 단일 책임
public User createUser(UserRequest request) {
    User user = validateAndNormalize(request);
    User savedUser = userRepository.save(user);
    emailService.sendWelcome(savedUser);
    return savedUser;
}
```

### 3. 주석보다 코드로 표현

```java
// Bad
// 성인인지 확인
if (user.getAge() >= 18) { ... }

// Good
if (isAdult(user)) { ... }

private boolean isAdult(User user) {
    final int ADULT_AGE = 18;
    return user.getAge() >= ADULT_AGE;
}
```

## Reactive Streams 특화 코드 품질

### 상태 관리

```java
// Bad: 일반 변수로 상태 관리
private boolean cancelled = false;
private long requested = 0;

// Good: Atomic 변수로 동시성 안전하게
private final AtomicBoolean cancelled = new AtomicBoolean(false);
private final AtomicLong requested = new AtomicLong(0);
```

### 규약 주석

```java
// Good: 규약 번호 명시
@Override
public void onSubscribe(Subscription s) {
    // Rule 2.13: Subscriber.onSubscribe must be called at most once
    if (this.subscription != null) {
        s.cancel();
        return;
    }
    this.subscription = s;
}
```

### 시그널 순서 검증

```java
// Good: 상태 전이 검증
private void signalNext(T item) {
    if (state.get() != State.ACTIVE) {
        // Rule 1.8: 이미 종료된 상태에서 onNext 금지
        return;
    }
    subscriber.onNext(item);
}
```

## SOLID 원칙

### Single Responsibility

```java
// Bad
class PublisherWithLogging<T> implements Publisher<T> {
    void subscribe(Subscriber<T> s) { ... }
    void log(String message) { ... }  // 다른 책임
}

// Good
class SimplePublisher<T> implements Publisher<T> { ... }
class LoggingSubscriber<T> implements Subscriber<T> { ... }  // 분리
```

### Open/Closed

```java
// Operator 패턴이 좋은 예
Publisher<Integer> result = source
    .map(x -> x * 2)      // 확장
    .filter(x -> x > 10)  // 확장
    .take(5);             // 확장
// 기존 코드 수정 없이 기능 추가
```

## 코드 스멜 체크리스트

| 스멜 | 증상 | Reactive에서의 해결 |
|------|------|---------------------|
| Long Method | 50줄 이상 | Operator로 분리 |
| Duplicate Code | 중복 로직 | 공통 Operator 추출 |
| Magic Numbers | 의미 없는 숫자 | 상수로 정의 |
| Mutable State | 변경 가능한 상태 | Atomic 또는 Immutable |

## 관련 스킬

- `testing`: 테스트 작성 전략
- `debugging`: 에러 추적 및 디버깅
- `reactive-spec`: Reactive Streams 규약
