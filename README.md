# Simple Reactive Streams

Reactive Streams 스펙을 직접 구현하며 학습하는 PBL(Problem-Based Learning) 프로젝트입니다.

## 왜 이 프로젝트인가?

Reactor나 RxJava를 사용하면서 이런 의문이 들었던 적 없나요?

- `request(n)`은 정확히 어떻게 동작하는 걸까?
- Backpressure는 내부적으로 어떻게 구현되어 있을까?
- `flatMap`은 왜 그렇게 복잡할까?

**직접 구현해보면 알 수 있습니다.**

이 프로젝트는 Reactive Streams의 4가지 핵심 인터페이스를 **바닥부터 직접 구현**하며, 39개 규약의 의미와 필요성을 체험하는 학습 프로젝트입니다.

## 학습 로드맵

| Module | 주제 | 시간 | 난이도 | 브랜치 |
|--------|------|------|--------|--------|
| 0 | 시작하기 | 30분 | ★☆☆☆☆ | `module-0-setup` |
| 1 | 핵심 인터페이스 | 1시간 | ★★☆☆☆ | `module-1-interfaces` |
| 2 | 첫 번째 Publisher | 2시간 | ★★★☆☆ | `module-2-publisher` |
| 3 | Backpressure | 2시간 | ★★★★☆ | `module-3-backpressure` |
| 4 | Operators | 3시간 | ★★★★☆ | `module-4-operators` |
| 5 | 에러 처리 | 1.5시간 | ★★★☆☆ | `module-5-errors` |
| 6 | Scheduler | 2.5시간 | ★★★★★ | `module-6-scheduler` |
| 7 | Hot vs Cold | 1.5시간 | ★★★☆☆ | `module-7-hot-cold` |
| 8 | TCK 검증 | 2시간 | ★★★☆☆ | `module-8-tck` |
| 9 | 실전 프로젝트 | 3~4시간 | ★★★★★ | `module-9-project` |

**총 학습 시간: 약 19~20시간**

## 브랜치 기반 학습

각 모듈은 독립된 브랜치로 관리됩니다. 원하는 단계부터 시작하거나, 막혔을 때 다음 단계의 정답을 참고할 수 있습니다.

### 특정 모듈부터 시작하기

```bash
# Module 2부터 시작하고 싶다면
git checkout module-2-publisher
```

### 다음 단계 정답 확인하기

```bash
# 현재 Module 2를 진행 중인데, Module 3의 정답이 궁금하다면
git diff module-2-publisher module-3-backpressure
```

### 특정 모듈의 변경사항만 보기

```bash
# Module 3에서 무엇이 추가되었는지 확인
git log module-2-publisher..module-3-backpressure --oneline
```

## 빠른 시작

### 1. 환경 확인

```bash
java -version    # Java 25 이상
./gradlew --version  # Gradle 8.14
```

### 2. 프로젝트 클론

```bash
git clone https://github.com/jaeyeonling/simple-reactive-streams.git
cd simple-reactive-streams
```

### 3. 빌드 확인

```bash
./gradlew build
```

### 4. 학습 시작

```bash
# Module 0부터 시작
git checkout module-0-setup

# 또는 원하는 모듈부터
git checkout module-2-publisher
```

## 학습 방법

### PBL (Problem-Based Learning) 사이클

각 모듈은 다음 5단계로 구성됩니다:

```
┌─────────────────────────────────────────────────────────────┐
│  1. 문제 제시 (Problem)                                      │
│     실제 시나리오 기반의 문제 상황                             │
├─────────────────────────────────────────────────────────────┤
│  2. 개념 탐구 (Concept)                                      │
│     문제 해결에 필요한 핵심 개념 학습                          │
├─────────────────────────────────────────────────────────────┤
│  3. 단계별 구현 (Implementation)                             │
│     스켈레톤 코드의 TODO 부분 완성                            │
├─────────────────────────────────────────────────────────────┤
│  4. 검증 (Verification)                                      │
│     테스트 코드와 TCK로 구현 검증                             │
├─────────────────────────────────────────────────────────────┤
│  5. 심화 학습 (Deep Dive)                                    │
│     개선점 탐구 및 실제 라이브러리 비교                        │
└─────────────────────────────────────────────────────────────┘
```

### 각 모듈 학습 흐름

1. **브랜치 체크아웃**: 해당 모듈 브랜치로 이동
2. **문서 읽기**: `docs/module-N-xxx/README.md` 학습
3. **코드 구현**: `src/main/java`의 TODO 부분 완성
4. **테스트 실행**: `./gradlew test`로 검증
5. **다음 모듈**: 완료 후 다음 브랜치로 이동

## 프로젝트 구조

```
src/main/java/io/simplereactive/
├── core/           # Publisher, Subscriber, Subscription, Processor, Flux 인터페이스
├── publisher/      # ArrayPublisher, RangePublisher, EmptyPublisher, ErrorPublisher, HotPublisher
├── subscriber/     # BufferedSubscriber, OverflowStrategy
├── subscription/   # BaseSubscription, ArraySubscription, BufferedSubscription
├── operator/       # Map, Filter, Take, OnErrorResume, SubscribeOn, PublishOn
├── scheduler/      # Scheduler, Schedulers, ImmediateScheduler, SingleThreadScheduler, ParallelScheduler
└── test/           # TestSubscriber, ManualSubscription

docs/               # PBL 학습 문서 (Module 0-9)
```

## 명령어

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

## Tech Stack

- **Language**: Java 25
- **Build**: Gradle 8.14 (Kotlin DSL)
- **Testing**: JUnit 6 + AssertJ + Reactive Streams TCK
- **Package**: io.simplereactive

## 참고 자료

- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Project Reactor](https://projectreactor.io/)
- [RxJava](https://github.com/ReactiveX/RxJava)

## License

MIT License
