---
name: testing
description: 테스트 작성 전략, JUnit 5, Reactive Streams TCK
---

# Testing Guide for Reactive Streams

## 테스트 피라미드

```
        /\
       /  \      TCK Tests (규약 준수 검증)
      /----\
     /      \    Integration Tests (Operator 체이닝)
    /--------\
   /          \  Unit Tests (개별 Publisher/Subscriber)
  /------------\
```

## JUnit 5 기본

### 테스트 클래스 구조

```java
class ArrayPublisherTest {

    @Test
    @DisplayName("request(n)만큼만 onNext 호출")
    void shouldEmitRequestedElements() {
        // Given
        ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2, 3, 4, 5);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        
        // When
        publisher.subscribe(subscriber);
        subscriber.request(3);
        
        // Then
        assertThat(subscriber.getReceivedItems()).containsExactly(1, 2, 3);
        assertThat(subscriber.isCompleted()).isFalse();
    }
    
    @Test
    @DisplayName("모든 요소 소비 후 onComplete 호출")
    void shouldCompleteAfterAllElements() {
        // Given
        ArrayPublisher<Integer> publisher = new ArrayPublisher<>(1, 2);
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        
        // When
        publisher.subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);
        
        // Then
        assertThat(subscriber.getReceivedItems()).containsExactly(1, 2);
        assertThat(subscriber.isCompleted()).isTrue();
    }
}
```

## TestSubscriber 구현

```java
public class TestSubscriber<T> implements Subscriber<T> {
    private Subscription subscription;
    private final List<T> receivedItems = new ArrayList<>();
    private Throwable error;
    private boolean completed = false;
    
    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
    }
    
    @Override
    public void onNext(T item) {
        receivedItems.add(item);
    }
    
    @Override
    public void onError(Throwable t) {
        this.error = t;
    }
    
    @Override
    public void onComplete() {
        this.completed = true;
    }
    
    // Helper methods
    public void request(long n) {
        subscription.request(n);
    }
    
    public void cancel() {
        subscription.cancel();
    }
    
    public List<T> getReceivedItems() {
        return Collections.unmodifiableList(receivedItems);
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public Throwable getError() {
        return error;
    }
}
```

## Reactive Streams TCK

TCK(Technology Compatibility Kit)는 Reactive Streams 규약 준수를 자동으로 검증합니다.

### TCK 테스트 클래스

```java
public class ArrayPublisherTckTest extends PublisherVerification<Integer> {

    public ArrayPublisherTckTest() {
        super(new TestEnvironment());
    }

    @Override
    public Publisher<Integer> createPublisher(long elements) {
        Integer[] array = new Integer[(int) elements];
        for (int i = 0; i < elements; i++) {
            array[i] = i;
        }
        return new ArrayPublisher<>(array);
    }

    @Override
    public Publisher<Integer> createFailedPublisher() {
        return new ErrorPublisher<>(new RuntimeException("Test error"));
    }
}
```

### TCK가 검증하는 규칙들

- `required_createPublisher1MustProduceAStreamOfExactly1Element`
- `required_spec101_subscriptionRequestMustResultInTheCorrectNumberOfProducedElements`
- `required_spec102_maySignalLessThanRequestedAndTerminateSubscription`
- `required_spec109_mustIssueOnSubscribeForNonNullSubscriber`
- ... (약 30개 이상의 테스트)

## StepVerifier 패턴

```java
public class StepVerifier<T> {
    private final Publisher<T> publisher;
    private final List<Consumer<T>> expectations = new ArrayList<>();
    
    public static <T> StepVerifier<T> create(Publisher<T> publisher) {
        return new StepVerifier<>(publisher);
    }
    
    public StepVerifier<T> expectNext(T expected) {
        expectations.add(actual -> 
            assertThat(actual).isEqualTo(expected)
        );
        return this;
    }
    
    public StepVerifier<T> expectNextCount(long count) {
        // count개의 요소 기대
        return this;
    }
    
    public void verifyComplete() {
        // 구독하고 검증 실행
    }
    
    public void verifyError(Class<? extends Throwable> errorClass) {
        // 에러 검증
    }
}

// 사용 예
StepVerifier.create(publisher)
    .expectNext(1)
    .expectNext(2)
    .expectNext(3)
    .verifyComplete();
```

## 동시성 테스트

```java
@Test
void shouldHandleConcurrentRequests() throws InterruptedException {
    ArrayPublisher<Integer> publisher = new ArrayPublisher<>(/* 1000 elements */);
    TestSubscriber<Integer> subscriber = new TestSubscriber<>();
    publisher.subscribe(subscriber);
    
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(100);
    
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> {
            subscriber.request(10);
            latch.countDown();
        });
    }
    
    latch.await(5, TimeUnit.SECONDS);
    
    // 총 1000개 요청, 1000개 요소
    assertThat(subscriber.getReceivedItems()).hasSize(1000);
}
```

## 테스트 체크리스트

### Publisher 테스트

- [ ] subscribe 시 onSubscribe 호출
- [ ] request(n)에 정확히 n개 이하 onNext
- [ ] 모든 요소 후 onComplete
- [ ] cancel 후 시그널 중지
- [ ] null 요소 시 onError(NPE)

### Subscriber 테스트

- [ ] onSubscribe에서 Subscription 저장
- [ ] request 호출로 demand 표현
- [ ] onError 후 시그널 무시
- [ ] onComplete 후 시그널 무시

## 관련 스킬

- `reactive-spec`: 규약 상세
- `debugging`: 테스트 실패 분석
