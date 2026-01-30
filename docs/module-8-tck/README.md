# Module 8: TCK 검증

> ⏱️ 예상 시간: 2시간 | ★★★☆☆ 난이도

## 학습 목표

- Reactive Streams TCK(Technology Compatibility Kit) 이해
- 어댑터 패턴으로 TCK 연동
- 공식 규약 준수 검증

---

## 1. TCK란?

### Technology Compatibility Kit

TCK는 Reactive Streams 스펙의 **공식 테스트 스위트**입니다.

```
┌─────────────────────────────────────────────────────────────┐
│                    Reactive Streams TCK                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  "우리 구현체가 스펙을 올바르게 준수하는가?"                   │
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  Rule 1.x   │    │  Rule 2.x   │    │  Rule 3.x   │     │
│  │  Publisher  │    │  Subscriber │    │ Subscription│     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 왜 TCK가 필요한가?

| 검증 방식 | 장점 | 단점 |
|----------|------|------|
| 직접 작성한 테스트 | 학습에 좋음 | 누락 가능성 |
| TCK | 공식 검증, 신뢰성 | 설정 복잡 |

**결론**: 둘 다 필요. 학습은 직접 테스트, 검증은 TCK.

---

## 2. 어댑터 패턴

### 문제: 인터페이스 불일치

```
우리 인터페이스                TCK 기대 인터페이스
─────────────────              ─────────────────
io.simplereactive.core.        org.reactivestreams.
  Publisher                      Publisher
  Subscriber                     Subscriber
  Subscription                   Subscription
```

### 해결: 어댑터로 래핑

```java
/**
 * 우리의 Publisher를 org.reactivestreams.Publisher로 어댑팅
 */
public class PublisherAdapter<T> implements org.reactivestreams.Publisher<T> {

    private final io.simplereactive.core.Publisher<T> delegate;

    public PublisherAdapter(io.simplereactive.core.Publisher<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void subscribe(org.reactivestreams.Subscriber<? super T> subscriber) {
        // TCK의 Subscriber를 우리의 Subscriber로 변환
        delegate.subscribe(new SubscriberAdapter<>(subscriber));
    }
}
```

### 어댑터 구조

```
TCK
 │
 ▼
org.reactivestreams.Publisher
 │
 ▼
PublisherAdapter ──────────────────┐
 │                                 │
 │ subscribe(org.rs.Subscriber)    │
 │        │                        │
 │        ▼                        │
 │ SubscriberAdapter               │
 │        │                        │
 │        ▼                        │
 └──> io.simplereactive.Publisher  │
              │                    │
              ▼                    │
      io.simplereactive.Subscriber │
              │                    │
              ▼                    │
      io.simplereactive.Subscription
              │
              ▼
      SubscriptionAdapter
              │
              ▼
      org.reactivestreams.Subscription
```

---

## 3. TCK 테스트 작성

### PublisherVerification 상속

```java
public class ArrayPublisherTckTest extends PublisherVerification<Integer> {

    public ArrayPublisherTckTest() {
        super(new TestEnvironment(100L));  // 타임아웃 100ms
    }

    /**
     * 테스트할 Publisher 생성
     */
    @Override
    public Publisher<Integer> createPublisher(long elements) {
        Integer[] array = new Integer[(int) elements];
        for (int i = 0; i < elements; i++) {
            array[i] = i;
        }
        return new PublisherAdapter<>(new ArrayPublisher<>(array));
    }

    /**
     * 실패하는 Publisher 생성 (에러 시나리오 테스트)
     */
    @Override
    public Publisher<Integer> createFailedPublisher() {
        return new PublisherAdapter<>(
            new ErrorPublisher<>(new RuntimeException("test error")));
    }

    /**
     * 최대 요소 수 (유한 Publisher)
     */
    @Override
    public long maxElementsFromPublisher() {
        return 1000L;
    }
}
```

### 테스트 실행

```bash
# TCK 테스트만 실행
./gradlew tckTest

# 모든 테스트 실행
./gradlew test tckTest
```

---

## 4. TCK 테스트 결과 이해

### 테스트 카테고리

| 접두사 | 의미 | 예시 |
|--------|------|------|
| `required_` | 필수 규약 | `required_spec101_...` |
| `optional_` | 선택 규약 | `optional_spec104_...` |
| `stochastic_` | 동시성 테스트 | `stochastic_spec103_...` |
| `untested_` | 자동 검증 불가 | `untested_spec106_...` |

### 결과 해석

```
ArrayPublisherTckTest
├── required_spec101_... PASSED    ← 규약 준수
├── required_spec102_... PASSED
├── optional_spec104_... PASSED
├── stochastic_spec103_... PASSED  ← 동시성 안전
├── untested_spec106_... SKIPPED   ← 자동 검증 불가
└── untested_spec107_... SKIPPED
```

### SKIPPED가 나오는 이유

`untested_*` 테스트는 TCK가 **자동으로 검증할 수 없는** 규약입니다:

| 테스트 | 이유 |
|--------|------|
| `untested_spec106` | 내부 상태 검증 필요 |
| `untested_spec107` | 타이밍 의존적 |
| `untested_spec304` | "무거운 계산" 정의 불가 |
| `untested_spec305` | 성능 측정 불가 |

**이것은 실패가 아닙니다!** TCK 설계상 자동 검증이 불가능한 항목입니다.

---

## 5. 현재 프로젝트 TCK 결과

### 테스트 현황

| Publisher | PASSED | SKIPPED | FAILED |
|-----------|--------|---------|--------|
| ArrayPublisher | 30 | 8 | 0 |
| RangePublisher | 30 | 8 | 0 |
| **총계** | **60** | **16** | **0** |

### 검증된 주요 규약

- ✅ Rule 1.1: subscribe 시 onSubscribe 호출
- ✅ Rule 1.9: null subscriber에 NPE
- ✅ Rule 2.13: null item 금지
- ✅ Rule 3.9: request(n <= 0) 시 에러
- ✅ Rule 3.13: cancel 멱등성
- ✅ ... 및 기타 모든 required/optional 규약

---

## 6. 체크포인트

- [ ] TCK의 목적 이해
- [ ] 어댑터 패턴 이해
- [ ] TCK 테스트 실행
- [ ] 결과 해석 (PASSED/SKIPPED 의미)
- [ ] 모든 required 테스트 통과

---

## 7. 다음 단계

TCK 검증을 통해 구현체의 **스펙 준수**를 확인했습니다.

다음 Module 9에서는 실제 프로젝트에 Reactive를 적용하는 방법을 학습합니다.

```bash
# 다음 모듈로
cd docs/module-9-real-world-project
```

---

## 참고 자료

- [Reactive Streams TCK](https://github.com/reactive-streams/reactive-streams-jvm/tree/master/tck)
- [TCK 사용 가이드](https://github.com/reactive-streams/reactive-streams-jvm/blob/master/README.md#specification)
