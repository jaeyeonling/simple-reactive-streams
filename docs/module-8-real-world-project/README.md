# Module 8: 실전 프로젝트 - 레거시 리팩터링

> ⏱️ 예상 시간: 3~4시간 | ★★★★★ 난이도

## 학습 목표

- Thread 기반 코드의 문제점 분석
- Reactive 방식으로 리팩터링
- 성능 비교 및 적용 가이드라인 학습

---

## 1. 레거시 코드 분석

### 시나리오: 상품 상세 정보 조회

```
사용자 요청 → 상품 API + 리뷰 API + 재고 API → 조합 → 응답
```

### 레거시 코드 (Thread 기반)

```java
public class LegacyProductService {
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public ProductDetail getProductDetail(String productId) {
        // 각 API 호출을 별도 스레드에서 실행
        Future<Product> productFuture = executor.submit(() -> 
            productApi.getProduct(productId)  // 200ms
        );
        Future<List<Review>> reviewsFuture = executor.submit(() -> 
            reviewApi.getReviews(productId)   // 300ms
        );
        Future<Inventory> inventoryFuture = executor.submit(() -> 
            inventoryApi.getInventory(productId)  // 150ms
        );
        
        try {
            // 모든 결과를 기다림 (블로킹!)
            Product product = productFuture.get(1, TimeUnit.SECONDS);
            List<Review> reviews = reviewsFuture.get(1, TimeUnit.SECONDS);
            Inventory inventory = inventoryFuture.get(1, TimeUnit.SECONDS);
            
            return new ProductDetail(product, reviews, inventory);
        } catch (Exception e) {
            throw new ServiceException("Failed to get product detail", e);
        }
    }
}
```

---

## 2. 문제점 식별

### 문제 1: 스레드 자원 낭비

```
요청 100개 × API 3개 = 300개의 작업
스레드 풀 10개로는 부족!

┌─────────────────────────────────────────────┐
│  Thread Pool (size: 10)                     │
│  [작업][작업][작업]...[대기][대기][대기]...    │
│   ↑↑↑                 ↑↑↑                  │
│  실행 중 (10개)        대기 중 (290개!)       │
└─────────────────────────────────────────────┘
```

### 문제 2: 블로킹

```java
Product product = productFuture.get();  // 스레드가 여기서 블로킹!
```

### 문제 3: 에러 처리 어려움

```java
try {
    Product product = productFuture.get();
    List<Review> reviews = reviewsFuture.get();  // 여기서 실패하면?
    Inventory inventory = inventoryFuture.get(); // 이건 성공했는데...
} catch (Exception e) {
    // 어떤 API가 실패했는지 알기 어려움
}
```

---

## 3. Reactive로 리팩터링

### Step 1: API를 Publisher로 변환

```java
package io.simplereactive.example;

import io.simplereactive.core.*;
import io.simplereactive.publisher.DeferPublisher;

public class ReactiveProductService {
    
    // 각 API 호출을 Publisher로 래핑
    private Publisher<Product> getProduct(String productId) {
        return new DeferPublisher<>(() -> 
            productApi.getProduct(productId)
        );
    }
    
    private Publisher<List<Review>> getReviews(String productId) {
        return new DeferPublisher<>(() -> 
            reviewApi.getReviews(productId)
        );
    }
    
    private Publisher<Inventory> getInventory(String productId) {
        return new DeferPublisher<>(() -> 
            inventoryApi.getInventory(productId)
        );
    }
}
```

### Step 2: Zip으로 조합

```java
public Publisher<ProductDetail> getProductDetail(String productId) {
    return ZipOperator.zip(
        getProduct(productId),
        getReviews(productId),
        getInventory(productId),
        (product, reviews, inventory) -> 
            new ProductDetail(product, reviews, inventory)
    );
}
```

### Step 3: Scheduler 적용

```java
public Publisher<ProductDetail> getProductDetail(String productId) {
    Scheduler ioScheduler = new ThreadPoolScheduler(20);
    
    return ZipOperator.zip(
        getProduct(productId).subscribeOn(ioScheduler),
        getReviews(productId).subscribeOn(ioScheduler),
        getInventory(productId).subscribeOn(ioScheduler),
        ProductDetail::new
    );
}
```

### Step 4: 에러 처리 추가

```java
public Publisher<ProductDetail> getProductDetail(String productId) {
    return ZipOperator.zip(
        getProduct(productId)
            .subscribeOn(ioScheduler)
            .onErrorResume(e -> defaultProduct()),
        getReviews(productId)
            .subscribeOn(ioScheduler)
            .onErrorResume(e -> emptyReviews()),  // 실패해도 빈 리스트
        getInventory(productId)
            .subscribeOn(ioScheduler)
            .onErrorResume(e -> unknownInventory()),
        ProductDetail::new
    );
}
```

---

## 4. Before vs After 비교

### 코드 비교

| 항목 | Before (Thread) | After (Reactive) |
|------|-----------------|------------------|
| 스레드 관리 | 직접 관리 | Scheduler 위임 |
| 에러 처리 | try-catch 중첩 | 선언적 체이닝 |
| 조합 로직 | Future.get() 블로킹 | Operator 조합 |
| 취소 지원 | 복잡함 | cancel() 한 번 |
| 가독성 | 명령형 | 선언적 |

### 성능 비교 (예상)

```
동시 요청 1000개 기준

Before (Thread Pool 10):
├── 평균 응답 시간: 높음 (스레드 경쟁)
├── 스레드 사용: 10개 고정, 나머지 대기
└── 메모리: Future 객체 누적

After (Reactive):
├── 평균 응답 시간: 낮음 (논블로킹)
├── 스레드 사용: 효율적 재사용
└── 메모리: Backpressure로 제어
```

---

## 5. 언제 Reactive를 써야 할까?

### ✅ 적합한 경우

1. **I/O 바운드 작업이 많을 때**
   - 여러 API 호출 조합
   - DB + 캐시 + 외부 서비스

2. **비동기 스트림 처리**
   - 실시간 데이터 피드
   - WebSocket, SSE

3. **Backpressure 필요**
   - 생산자 > 소비자 속도
   - 메모리 제한 환경

### ❌ 과한 경우

1. **단순 CRUD**
   - 복잡도만 증가

2. **CPU 바운드 작업**
   - 계산 위주는 이득 없음

3. **팀 준비 안 됨**
   - 디버깅 어려움
   - 학습 비용

### 💡 실용적 조언

> "모든 것을 Reactive로 바꿀 필요 없다.
> 병목 지점만 선택적으로 적용하라."

---

## 6. 체크포인트

- [ ] 레거시 코드 문제점 분석 완료
- [ ] Publisher로 API 래핑
- [ ] Zip으로 조합
- [ ] Scheduler 적용
- [ ] 에러 처리 추가
- [ ] 성능 비교 이해

---

## 7. 학습 완료!

축하합니다! Reactive Streams의 핵심을 모두 학습했습니다.

### 배운 내용 요약

```
Module 0: 왜 Reactive인가?
Module 1: Publisher, Subscriber, Subscription, Processor
Module 2: ArrayPublisher 구현
Module 3: Backpressure와 request(n)
Module 4: map, filter, take Operator
Module 5: 에러 처리와 전파
Module 6: Scheduler와 비동기
Module 7: Hot vs Cold Publisher
Module 8: 실전 리팩터링
```

### 다음 단계

1. **TCK 테스트 통과** - 모든 구현체가 규약을 준수하는지 검증
2. **Reactor 소스 읽기** - 실제 라이브러리 구현 비교
3. **프로젝트 적용** - 실제 프로젝트에 적용해보기

```bash
# TCK 테스트 실행
./gradlew test --tests "*Tck*"
```

---

## 참고 자료

- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/)
- [RxJava Wiki](https://github.com/ReactiveX/RxJava/wiki)
