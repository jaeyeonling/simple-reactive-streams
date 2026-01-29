---
description: 데이터 흐름 시각화
agent: tutor
subtask: true
---

시각화 대상: $ARGUMENTS

Reactive Streams의 데이터 흐름을 ASCII 다이어그램으로 시각화합니다.

## 사용 예시

- `/visualize subscribe` - 구독 흐름 시각화
- `/visualize request` - request/onNext 흐름
- `/visualize backpressure` - Backpressure 동작
- `/visualize operator` - Operator 체이닝
- `/visualize error` - 에러 전파 흐름
- `/visualize scheduler` - 스레드 전환 흐름
- `/visualize hot-cold` - Hot/Cold Publisher 비교

## 출력 예시

```
Subscriber                    Publisher
    |                             |
    |-------- subscribe --------->|
    |<------- onSubscribe --------|
    |                             |
    |-------- request(2) -------->|
    |<------- onNext(1) ----------|
    |<------- onNext(2) ----------|
    |                             |
    |-------- request(1) -------->|
    |<------- onNext(3) ----------|
    |<------- onComplete ---------|
```

인자가 없으면 기본적인 Publisher-Subscriber 상호작용을 보여줍니다.
