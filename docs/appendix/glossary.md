# 용어집 (Glossary)

## A

### Async (비동기)
작업의 완료를 기다리지 않고 다음 작업을 진행하는 방식. Reactive Streams는 비동기 스트림 처리를 위한 표준.

## B

### Backpressure (배압)
빠른 생산자가 느린 소비자를 압도하지 않도록 흐름을 제어하는 메커니즘. `request(n)`을 통해 구현.

### Bounded (유한)
크기나 개수가 제한된 상태. 예: bounded buffer, bounded stream

## C

### Cancel (취소)
`Subscription.cancel()`을 호출하여 더 이상 데이터를 받지 않겠다고 선언.

### Cold Publisher
구독할 때마다 처음부터 데이터를 생성하는 Publisher. 예: DB 쿼리, HTTP 요청

### Complete (완료)
모든 데이터 발행이 끝났음을 나타내는 터미널 시그널. `onComplete()` 호출.

## D

### Demand (수요)
Subscriber가 처리할 준비가 된 데이터의 양. `request(n)`으로 표현.

### Downstream (하류)
데이터 흐름에서 소비자 방향. Operator에서 보면 결과를 받는 쪽.

## E

### Error (에러)
처리 중 발생한 예외 상황. `onError(Throwable)` 시그널로 전달.

## F

### Fluent API
메서드 체이닝을 통해 읽기 쉬운 코드를 작성하는 API 스타일.
```java
publisher.map(...).filter(...).subscribe(...)
```

## H

### Hot Publisher
구독 시점과 관계없이 데이터를 발행하는 Publisher. 예: 주식 시세, 센서 데이터

## M

### Marble Diagram
데이터 스트림을 시간 축을 따라 시각화한 다이어그램.
```
──1──2──3──|
```

## O

### onComplete
모든 데이터 발행 완료 시그널. 터미널 상태.

### onError
에러 발생 시그널. 터미널 상태.

### onNext
데이터 아이템 전달 시그널.

### onSubscribe
구독 시작 시그널. Subscription을 전달받음.

### Operator
Publisher를 입력받아 변환된 새 Publisher를 반환하는 함수. 예: map, filter, flatMap

## P

### Processor
Publisher이자 Subscriber. 데이터를 받아 변환 후 다시 발행.

### Publisher
데이터 스트림을 발행하는 주체. `subscribe()`로 구독을 받음.

### publishOn
이후 시그널이 발행되는 스레드를 지정하는 Operator.

## R

### Reactive Streams
비동기 스트림 처리를 위한 표준 인터페이스 규약. 4개의 인터페이스와 39개의 규칙.

### Request (요청)
`Subscription.request(n)`으로 n개의 데이터를 요청.

## S

### Scheduler
작업이 실행될 스레드를 결정하는 컴포넌트.

### Signal (시그널)
Publisher와 Subscriber 사이의 통신 단위. onSubscribe, onNext, onError, onComplete.

### Subscriber
데이터 스트림을 구독하고 처리하는 주체.

### Subscription
Publisher와 Subscriber 간의 구독 관계. request와 cancel을 제공.

### subscribeOn
구독이 시작되는 스레드를 지정하는 Operator.

## T

### TCK (Technology Compatibility Kit)
구현체가 Reactive Streams 규약을 준수하는지 검증하는 테스트 키트.

### Terminal Signal (터미널 시그널)
스트림의 종료를 나타내는 시그널. onComplete 또는 onError.

## U

### Unbounded (무한)
크기나 개수에 제한이 없는 상태. `request(Long.MAX_VALUE)`는 unbounded request.

### Upstream (상류)
데이터 흐름에서 생산자 방향. Operator에서 보면 데이터를 받는 쪽.
