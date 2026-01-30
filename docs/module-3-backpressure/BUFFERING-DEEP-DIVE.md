# BufferedSubscriber 심층 분석

> BufferedSubscriber의 핵심 동작인 Prefetch, Replenish, Overflow 처리를 상세히 이해합니다.

## 핵심 개념: 버퍼는 "속도 조절"이 아닌 "완충"

```
흔한 오해:
  BufferedSubscriber가 속도를 조절한다 ❌

실제 동작:
  BufferedSubscriber는 속도 차이를 "완충"한다 ✅
  실제 속도 조절은 Downstream의 request(n)이 담당
```

---

## 1. 전체 구조

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BufferedSubscriber                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Upstream                                         Downstream       │
│  (Publisher)                                      (Subscriber)      │
│      │                                                 │            │
│      │                  ┌─────────────┐               │            │
│      │   onNext(item)   │             │   onNext(item)│            │
│      │─────────────────>│   Buffer    │──────────────>│            │
│      │                  │  [1][2][3]  │               │            │
│      │                  │             │               │            │
│      │   request(n)     │             │   request(n)  │            │
│      │<─────────────────│             │<──────────────│            │
│      │                  └─────────────┘               │            │
│      │                        │                       │            │
│      │                        │                       │            │
│      │              ┌─────────┴─────────┐             │            │
│      │              │                   │             │            │
│      │          Prefetch            Replenish         │            │
│      │       (구독 시 미리 요청)    (소비 후 다시 요청)  │            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Prefetch (미리 당겨오기)

### 개념

구독 시작 시 **버퍼 크기만큼 미리 요청**하여 버퍼를 채워둡니다.

```java
// BufferedSubscriber.onSubscribe()
public void onSubscribe(Subscription s) {
    // ...
    s.request(bufferSize);  // ⭐ Prefetch
}
```

### 왜 필요한가?

```
[Prefetch 없이]

시간 ──────────────────────────────────────────────>

t=0   downstream.request(1)
      │
      └──> upstream.request(1)
           │
           └──> [네트워크 대기...]
                │
t=100            └──> onNext(data1)
                      │
                      └──> downstream.onNext(data1)

t=100 downstream.request(1)
      │
      └──> upstream.request(1)
           │
           └──> [네트워크 대기...]
                │
t=200            └──> onNext(data2)

총 시간: 200ms (매번 대기)
```

```
[Prefetch 있으면]

시간 ──────────────────────────────────────────────>

t=0   구독 시작 → upstream.request(5) [Prefetch]
      │
      └──> [네트워크 대기...]
           │
t=100      └──> onNext(data1, data2, data3, data4, data5)
                │
                └──> buffer: [1, 2, 3, 4, 5]

t=100 downstream.request(1)
      │
      └──> buffer에서 즉시 제공! (대기 없음)

t=100 downstream.request(1)
      │
      └──> buffer에서 즉시 제공! (대기 없음)

총 시간: 100ms (최초 1회만 대기)
```

### 코드 흐름

```
┌──────────────────────────────────────────────────────────────┐
│ 1. publisher.subscribe(bufferedSubscriber)                   │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. bufferedSubscriber.onSubscribe(upstreamSubscription)      │
│                                                              │
│    this.subscription = new BufferedSubscription(...);        │
│    downstream.onSubscribe(this.subscription);                │
│    upstreamSubscription.request(bufferSize);  ← Prefetch     │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. upstream이 bufferSize만큼 데이터 발행                      │
│                                                              │
│    buffer: [data1, data2, data3, ...]                        │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Replenish (다시 채우기)

### 개념

Downstream에 데이터를 전달한 후, **전달한 만큼 Upstream에 다시 요청**합니다.

```java
// BufferedSubscription.drainLoop()
private boolean drainLoop() {
    long emitted = emitFromBuffer();  // downstream으로 전달
    
    if (emitted > 0) {
        replenishUpstream(emitted);   // ⭐ Replenish
    }
}

private void replenishUpstream(long n) {
    upstream.request(n);  // 소비한 만큼 다시 요청
}
```

### 왜 필요한가?

```
[Replenish 없이]

t=0  buffer: [1, 2, 3, 4, 5]  (Prefetch로 채움)
     
t=1  downstream.request(5) → 5개 전달
     buffer: []  ← 비어버림!
     
t=2  downstream.request(1)
     buffer: []  ← 줄 게 없음!
     │
     └──> upstream.request(1)  ← 이제서야 요청
          │
          └──> [대기...]

→ 버퍼가 비어서 다시 대기 시간 발생
```

```
[Replenish 있으면]

t=0  buffer: [1, 2, 3, 4, 5]  (Prefetch로 채움)
     
t=1  downstream.request(3) → 3개 전달
     buffer: [4, 5]
     │
     └──> upstream.request(3)  ← Replenish: 3개 다시 요청
          │
          └──> 백그라운드에서 데이터 도착
               buffer: [4, 5, 6, 7, 8]  ← 다시 채워짐

t=2  downstream.request(3) → 즉시 3개 전달 가능
     buffer: [8]
     │
     └──> upstream.request(3)  ← Replenish

→ 버퍼가 계속 채워져서 대기 없음
```

### 코드 흐름

```
┌──────────────────────────────────────────────────────────────┐
│ 1. downstream.request(3)                                     │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. BufferedSubscription.request(3)                           │
│                                                              │
│    demand += 3  (demand: 0 → 3)                              │
│    drain()                                                   │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. drain() → drainLoop()                                     │
│                                                              │
│    emitFromBuffer():                                         │
│      - buffer에서 3개 꺼냄                                    │
│      - downstream.onNext(1)                                  │
│      - downstream.onNext(2)                                  │
│      - downstream.onNext(3)                                  │
│      - return 3 (emitted)                                    │
│                                                              │
│    demand -= 3  (demand: 3 → 0)                              │
│                                                              │
│    replenishUpstream(3):  ← Replenish                        │
│      - upstream.request(3)                                   │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ 4. upstream이 3개 더 발행                                     │
│                                                              │
│    buffer: [4, 5] → [4, 5, 6, 7, 8]                          │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Overflow 처리

### 발생 조건

Upstream이 규약을 어기거나, 동기적으로 데이터를 발행할 때 버퍼가 가득 찰 수 있습니다.

```
정상 상황:
  request(5) → 5개만 발행 → 버퍼 OK

문제 상황:
  request(5) → 10개 발행 (규약 위반!) → 버퍼 초과
  
또는:
  
  request(5) → 5개 발행 → 버퍼 가득
  downstream이 아직 소비 안 함
  upstream이 동기적으로 더 발행 (onNext 내에서 request 호출 시)
```

### 3가지 전략

```
┌─────────────────────────────────────────────────────────────────┐
│                    Buffer: [A][B][C][D][E]                      │
│                           (가득 참!)                             │
│                                                                 │
│                    새 데이터 'F' 도착                            │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
   │ DROP_OLDEST │     │ DROP_LATEST │     │    ERROR    │
   ├─────────────┤     ├─────────────┤     ├─────────────┤
   │             │     │             │     │             │
   │ [B][C][D]   │     │ [A][B][C]   │     │ onError()   │
   │ [E][F]      │     │ [D][E]      │     │             │
   │             │     │             │     │ upstream    │
   │ A 버림      │     │ F 버림      │     │ .cancel()   │
   │             │     │             │     │             │
   └─────────────┘     └─────────────┘     └─────────────┘
         │                   │                   │
         ▼                   ▼                   ▼
   실시간 모니터링      샘플링/배치        데이터 무결성
   최신 데이터 중요     순서 유지 중요     손실 불가
```

### 코드

```java
// BufferedSubscription.handleOverflow()
private void handleOverflow(T item) {
    switch (strategy) {
        case DROP_OLDEST -> {
            pollFromBuffer();   // 가장 오래된 것 제거
            addToBuffer(item);  // 새 것 추가
        }
        case DROP_LATEST -> {
            // 새 아이템 무시 (아무것도 안 함)
        }
        case ERROR -> {
            upstream.cancel();
            error = new BufferOverflowException(bufferSize);
            terminated.set(true);
        }
    }
}
```

---

## 5. 전체 흐름 시각화

### 시나리오: 버퍼 크기 3, DROP_OLDEST

```
┌────────────────────────────────────────────────────────────────────┐
│ t=0: 구독 시작                                                      │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   Upstream              Buffer              Downstream             │
│      │                 [   ]                    │                  │
│      │                                          │                  │
│      │◄─── request(3) ─┤     Prefetch           │                  │
│      │                 │                        │                  │
│                                                                    │
│   상태: demand(upstream→buffer) = 3                                │
│         demand(buffer→downstream) = 0                              │
│         buffer = []                                                │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ t=1: Upstream이 데이터 발행                                         │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   Upstream              Buffer              Downstream             │
│      │                 [1][2][3]                │                  │
│      │                                          │                  │
│      │─── onNext(1) ──►│                        │                  │
│      │─── onNext(2) ──►│                        │                  │
│      │─── onNext(3) ──►│                        │                  │
│      │                 │                        │                  │
│                                                                    │
│   상태: buffer = [1, 2, 3] (가득 참!)                               │
│         downstream은 아직 request 안 함                             │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ t=2: Upstream이 계속 발행 (Overflow!)                               │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   Upstream              Buffer              Downstream             │
│      │                 [3][4][5]                │                  │
│      │                                          │                  │
│      │─── onNext(4) ──►│  DROP_OLDEST           │                  │
│      │                 │  1 버림, 4 추가        │                  │
│      │─── onNext(5) ──►│  2 버림, 5 추가        │                  │
│      │                 │                        │                  │
│      │─── onComplete ─►│                        │                  │
│                                                                    │
│   상태: buffer = [3, 4, 5]                                         │
│         upstream 완료, downstream 대기                              │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│ t=3: Downstream이 request                                          │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   Upstream              Buffer              Downstream             │
│      │                 [   ]                    │                  │
│      │                                          │                  │
│      │                 │─── onNext(3) ────────►│                  │
│      │                 │─── onNext(4) ────────►│                  │
│      │                 │─── onNext(5) ────────►│                  │
│      │                 │─── onComplete ───────►│                  │
│      │                 │                        │                  │
│                                                                    │
│   결과: downstream.items = [3, 4, 5]                               │
│         1, 2는 DROP_OLDEST로 손실                                   │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 6. 핵심 정리

| 메커니즘 | 시점 | 동작 | 목적 |
|---------|------|------|------|
| **Prefetch** | 구독 시작 | `upstream.request(bufferSize)` | 대기 시간 최소화 |
| **Buffering** | onNext 수신 | 버퍼에 저장 | 속도 차이 완충 |
| **Drain** | downstream request | 버퍼→downstream 전달 | demand만큼 전달 |
| **Replenish** | drain 후 | `upstream.request(emitted)` | 버퍼 다시 채우기 |
| **Overflow** | 버퍼 초과 시 | 전략에 따라 처리 | 데이터 손실 관리 |

### 버퍼가 하는 것 vs 안 하는 것

```
✅ 버퍼가 하는 것:
   - 데이터 임시 저장
   - Prefetch로 대기 시간 줄이기
   - Replenish로 버퍼 유지
   - Overflow 시 전략 적용

❌ 버퍼가 안 하는 것:
   - 속도 결정 (downstream의 request가 결정)
   - 데이터 생산 (upstream이 담당)
   - 최종 처리 (downstream이 담당)
```

---

## 다음 단계

이 개념들을 테스트로 직접 확인해보세요:

```java
// BufferedSubscriberFlowTest.java 참고
@Nested
class PrefetchBehavior { ... }

@Nested  
class ReplenishBehavior { ... }

@Nested
class OverflowBehavior { ... }
```
