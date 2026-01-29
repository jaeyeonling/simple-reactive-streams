# Module 5: 에러 처리

> ⏱️ 예상 시간: 1.5시간 | ★★★☆☆ 난이도

## 학습 목표

- onError 규약 이해
- 에러 전파 메커니즘 학습
- 에러 복구 Operator 구현

---

## 1. 문제 제시

### 시나리오

```java
publisher
    .map(x -> {
        if (x == 3) throw new RuntimeException("Error!");
        return x * 2;
    })
    .subscribe(subscriber);
```

x가 3일 때 예외가 발생합니다. 어떻게 처리해야 할까요?

---

## 2. 개념 설명

### 에러 전파 규칙

```
┌─────────────────────────────────────────────────────┐
│  Rule 1.4: 에러 발생 시 onError를 시그널해야 한다      │
│  Rule 1.7: onError 후에는 어떤 시그널도 오면 안 된다   │
└─────────────────────────────────────────────────────┘

정상 흐름:
onSubscribe → onNext → onNext → onComplete

에러 흐름:
onSubscribe → onNext → onError → [종료]
                          ↑
                    더 이상 시그널 없음!
```

### 에러 시각화

```
Source ──1──2──✗──────────────────────────────>
              │
              │ 에러 발생!
              ▼
         onError(e) 전파
              │
Subscriber <──┘

이후:
- onNext 금지
- onComplete 금지
- Subscription 취소됨
```

---

## 3. 구현 가이드

### Step 1: 에러 발행 Publisher

```java
package io.simplereactive.publisher;

import io.simplereactive.core.*;

/**
 * 즉시 에러를 발행하는 Publisher.
 */
public class ErrorPublisher<T> implements Publisher<T> {
    
    private final Throwable error;
    
    public ErrorPublisher(Throwable error) {
        this.error = error;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                // 아무것도 안 함
            }
            
            @Override
            public void cancel() {
                // 아무것도 안 함
            }
        });
        subscriber.onError(error);
    }
}
```

### Step 2: Map에서 에러 처리

```java
@Override
public void onNext(T item) {
    R mapped;
    try {
        mapped = mapper.apply(item);
    } catch (Throwable t) {
        // 변환 중 에러 발생 → onError로 전파
        upstream.cancel();  // 상위 스트림 취소
        downstream.onError(t);
        return;
    }
    downstream.onNext(mapped);
}
```

### Step 3: OnErrorResume Operator

에러 발생 시 대체 Publisher로 전환:

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import java.util.function.Function;

/**
 * 에러 발생 시 대체 Publisher로 전환하는 Operator.
 */
public class OnErrorResumeOperator<T> implements Publisher<T> {
    
    private final Publisher<T> upstream;
    private final Function<Throwable, Publisher<T>> fallback;
    
    public OnErrorResumeOperator(
            Publisher<T> upstream,
            Function<Throwable, Publisher<T>> fallback) {
        this.upstream = upstream;
        this.fallback = fallback;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        upstream.subscribe(new OnErrorResumeSubscriber<>(downstream, fallback));
    }
    
    static class OnErrorResumeSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> downstream;
        private final Function<Throwable, Publisher<T>> fallback;
        private Subscription upstream;
        
        // 생성자 생략...
        
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(s);
        }
        
        @Override
        public void onNext(T item) {
            downstream.onNext(item);
        }
        
        @Override
        public void onError(Throwable t) {
            // 에러 발생! 대체 Publisher로 전환
            Publisher<T> fallbackPublisher = fallback.apply(t);
            fallbackPublisher.subscribe(new FallbackSubscriber<>(downstream));
        }
        
        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }
    
    static class FallbackSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> downstream;
        
        FallbackSubscriber(Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);  // fallback은 모두 요청
        }
        
        @Override
        public void onNext(T item) {
            downstream.onNext(item);
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

---

## 4. 사용 예시

```java
// 에러 발생 시 기본값으로 대체
publisher
    .map(x -> riskyOperation(x))
    .onErrorResume(error -> new ArrayPublisher<>(-1))  // 에러 시 -1 반환
    .subscribe(subscriber);

// 에러 발생 시 빈 스트림
publisher
    .onErrorResume(error -> new EmptyPublisher<>())
    .subscribe(subscriber);

// 에러 종류에 따라 다른 처리
publisher
    .onErrorResume(error -> {
        if (error instanceof TimeoutException) {
            return cachedPublisher;
        }
        return new ErrorPublisher<>(error);  // 다시 에러 발생
    })
    .subscribe(subscriber);
```

---

## 5. 테스트

```java
@Test
@DisplayName("에러 발생 시 fallback으로 전환")
void onErrorResume() {
    Publisher<Integer> failing = subscriber -> {
        subscriber.onSubscribe(new SimpleSubscription());
        subscriber.onNext(1);
        subscriber.onNext(2);
        subscriber.onError(new RuntimeException("Oops!"));
    };
    
    TestSubscriber<Integer> subscriber = new TestSubscriber<>();
    
    new OnErrorResumeOperator<>(
        failing,
        error -> new ArrayPublisher<>(-1, -2)
    ).subscribe(subscriber);
    
    subscriber.request(Long.MAX_VALUE);
    
    assertThat(subscriber.received).containsExactly(1, 2, -1, -2);
    assertThat(subscriber.completed).isTrue();
    assertThat(subscriber.error).isNull();
}
```

---

## 6. 체크포인트

- [ ] ErrorPublisher 구현
- [ ] MapOperator 에러 처리 추가
- [ ] OnErrorResumeOperator 구현
- [ ] 테스트 통과

```bash
/check 5
```

---

## 다음 단계

Module 6에서는 Scheduler와 비동기를 학습합니다.

```bash
/learn 6
```
