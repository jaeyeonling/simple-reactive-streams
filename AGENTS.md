# Simple Reactive Streams

Reactive Streams 스펙을 직접 구현하며 학습하는 PBL(Problem-Based Learning) 프로젝트

## Tech Stack

- Language: Java 25
- Build: Gradle 8.14 (Kotlin DSL)
- Testing: JUnit 6 + Reactive Streams TCK
- Package: io.simplereactive

## Commands

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# TCK 테스트 (규약 검증)
./gradlew test --tests "*Tck*"

# 특정 모듈 테스트
./gradlew test --tests "io.simplereactive.publisher.*"
```

## Project Structure

```
src/main/java/io/simplereactive/
├── core/           # Publisher, Subscriber, Subscription, Processor 인터페이스
├── publisher/      # ArrayPublisher, RangePublisher, EmptyPublisher 등
├── subscriber/     # LoggingSubscriber, BufferedSubscriber
├── subscription/   # BaseSubscription, SubscriptionState (상태 머신)
├── operator/       # Map, Filter, FlatMap, Take 등
├── scheduler/      # ImmediateScheduler, ThreadPoolScheduler
├── support/        # SignalLogger, RuleValidator, MarbleDiagram
└── test/           # TestSubscriber, StepVerifier

docs/               # PBL 학습 문서 (Module 0-8)
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
