# Simple Reactive Streams

Reactive Streams 스펙을 직접 구현하며 학습하는 PBL(Problem-Based Learning) 프로젝트

## Tech Stack

- Language: Java 25
- Build: Gradle 8.14 (Kotlin DSL)
- Testing: JUnit 6 + Reactive Streams TCK (TestNG)
- Package: io.simplereactive

## Commands

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# TCK 테스트 (규약 검증)
./gradlew tckTest

# 특정 모듈 테스트
./gradlew test --tests "io.simplereactive.publisher.*"
```

## Project Structure

```
src/main/java/io/simplereactive/
├── core/           # Publisher, Subscriber, Subscription, Processor, Flux, ConnectableFlux
├── publisher/      # ArrayPublisher, RangePublisher, EmptyPublisher, ErrorPublisher, HotPublisher
├── subscriber/     # BufferedSubscriber, OverflowStrategy
├── subscription/   # BaseSubscription, ArraySubscription, BufferedSubscription, Subscriptions
├── operator/       # Map, Filter, Take, OnErrorResume, OnErrorReturn, SubscribeOn, PublishOn
├── scheduler/      # Scheduler, Schedulers, ImmediateScheduler, SingleThreadScheduler, ParallelScheduler
└── test/           # TestSubscriber, ManualSubscription

src/test/java/io/simplereactive/
├── tck/            # TCK 어댑터 및 검증 테스트 (Module 8)
│   └── adapter/    # PublisherAdapter, SubscriberAdapter, SubscriptionAdapter
└── ...             # 단위 테스트 (JUnit)

docs/               # PBL 학습 문서 (Module 0-9)
```

## Learning Modules

```
Module 0: 시작하기           - 환경 설정, Reactive 소개
Module 1: 핵심 인터페이스     - Publisher, Subscriber, Subscription, Processor
Module 2: 첫 번째 Publisher  - ArrayPublisher 구현
Module 3: Backpressure      - request(n), Demand 관리
Module 4: Operators         - map, filter, take
Module 5: 에러 처리          - onError, 복구 전략
Module 6: Scheduler         - subscribeOn, publishOn
Module 7: Hot vs Cold       - HotPublisher, ConnectableFlux
Module 8: TCK 검증          - Reactive Streams TCK로 규약 검증
Module 9: 레거시 리팩터링     - 실전 프로젝트
```

## Code Style

- 모든 public 클래스/메서드에 Javadoc 작성 (학습 목적)
- Reactive Streams 규약 번호를 주석으로 명시 (예: `// Rule 1.3`)
- 상태 변경은 반드시 Atomic 연산 사용
- 학습용이므로 성능보다 가독성/명확성 우선

## IMPORTANT

- Reactive Streams 규약 위반 시 명확한 예외(IllegalStateException) 발생 필수
- onNext/onError/onComplete 호출 전 상태 검증 필수
- 모든 시그널은 SignalLogger로 추적 가능하도록 설계
- TCK 테스트 통과가 구현 완료의 기준

## 학습 도구

```
/learn [module]    - 모듈 학습 시작
/check [module]    - 구현 검증
/hint [topic]      - 힌트 제공
/visualize [flow]  - 데이터 흐름 시각화
/spec [rule]       - Reactive Streams 규약 조회
@tutor             - Reactive Streams 개념 질문
@checker           - 구현 코드 검증
```
