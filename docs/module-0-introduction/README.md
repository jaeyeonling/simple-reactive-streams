# Module 0: 시작하기

> ⏱️ 예상 시간: 30분 | ★☆☆☆☆ 난이도

## 학습 목표

- Reactive 프로그래밍이 왜 필요한지 이해
- Reactive Streams 스펙의 역할과 구성 파악
- 개발 환경 설정 완료

---

## 1. 왜 Reactive인가?

### 전통적 방식의 한계

```java
// 동기 블로킹 방식
public List<Product> getProducts() {
    List<Product> products = productApi.fetchAll();    // 블로킹 200ms
    List<Price> prices = priceApi.fetchAll();          // 블로킹 150ms
    List<Stock> stocks = stockApi.fetchAll();          // 블로킹 100ms
    
    return combine(products, prices, stocks);          // 총 450ms 대기
}
```

**문제점**:
- 스레드가 I/O 대기 중 아무것도 못함
- 동시 요청이 많으면 스레드 풀 고갈
- 확장성 한계

### 콜백 방식의 문제

```java
// 콜백 지옥
productApi.fetchAll(products -> {
    priceApi.fetchAll(prices -> {
        stockApi.fetchAll(stocks -> {
            // 깊은 중첩
            // 에러 처리 어려움
            // 취소 어려움
        });
    });
});
```

### Reactive의 해결책

```java
// Reactive 방식
Flux.zip(
    productApi.fetchAllReactive(),
    priceApi.fetchAllReactive(),
    stockApi.fetchAllReactive()
)
.map(tuple -> combine(tuple))
.subscribe(result -> handle(result));

// 논블로킹, 선언적, 조합 가능
```

---

## 2. Reactive Streams란?

### 정의

> 비동기 스트림 처리를 위한 **표준 인터페이스 규약**

### 핵심 4가지 인터페이스

```
┌─────────────────────────────────────────────────────────────┐
│                    Reactive Streams                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Publisher<T>        데이터를 발행하는 주체                  │
│       │                                                     │
│       │ subscribe                                           │
│       ▼                                                     │
│   Subscriber<T>       데이터를 구독하는 주체                  │
│       │                                                     │
│       │ onSubscribe(Subscription)                           │
│       ▼                                                     │
│   Subscription        Publisher-Subscriber 간의 계약         │
│       │                                                     │
│       │ request(n) / cancel()                               │
│       ▼                                                     │
│   Processor<T,R>      Publisher + Subscriber (변환)          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 시그널 흐름

```
Subscriber                         Publisher
    │                                  │
    │──────── subscribe ──────────────>│
    │                                  │
    │<─────── onSubscribe(s) ──────────│
    │                                  │
    │──────── s.request(n) ───────────>│
    │                                  │
    │<─────── onNext(data) ────────────│  ×n회
    │                                  │
    │<─────── onComplete() ────────────│  또는
    │         또는 onError(e) ─────────│
    │                                  │
```

### 규약 (Specification)

Reactive Streams는 **39개의 규칙**으로 구성:
- Publisher 규칙: 11개
- Subscriber 규칙: 13개
- Subscription 규칙: 17개
- Processor 규칙: 2개

```bash
# 규칙 조회
/spec 1      # Publisher 규칙
/spec 2      # Subscriber 규칙
/spec 3      # Subscription 규칙
```

---

## 3. 개발 환경 설정

### 필수 요구사항

- Java 25 이상
- Gradle 8.14 이상

### 환경 확인

```bash
# Java 버전 확인
java -version

# Gradle 버전 확인 (wrapper 사용)
./gradlew --version
```

### 프로젝트 빌드

```bash
# 빌드
./gradlew build

# 테스트 실행
./gradlew test
```

### IDE 설정 (선택)

IntelliJ IDEA:
1. File → Open → 프로젝트 폴더 선택
2. Gradle 프로젝트로 Import
3. JDK 25 설정 확인

---

## 4. 프로젝트 구조 이해

```
simple-reactivestreams/
├── src/main/java/io/simplereactive/
│   ├── core/           # 핵심 인터페이스 (여기서 시작!)
│   ├── publisher/      # Publisher 구현체
│   ├── subscriber/     # Subscriber 구현체
│   ├── subscription/   # Subscription 관련
│   ├── operator/       # Operator (map, filter 등)
│   ├── scheduler/      # 스레드 관리
│   ├── support/        # 학습 지원 도구
│   └── test/           # 테스트 유틸리티
│
├── src/test/java/io/simplereactive/
│   ├── tck/            # TCK 테스트 (규약 검증)
│   └── ...             # 단위 테스트
│
└── docs/               # 학습 문서 (현재 위치)
```

---

## 5. 체크포인트

다음 체크리스트를 완료하면 Module 0 완료입니다:

- [ ] Java 25 설치 확인
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 실행 (아직 테스트 없어도 OK)
- [ ] Reactive Streams의 4가지 인터페이스 이해
- [ ] 시그널 흐름 이해

---

## 다음 단계

Module 1에서는 4가지 핵심 인터페이스를 직접 정의합니다.

```bash
# OpenCode에서:
/learn 1
```

---

## 참고

- [Reactive Streams 공식 사이트](https://www.reactive-streams.org/)
- [Reactive Manifesto](https://www.reactivemanifesto.org/)
