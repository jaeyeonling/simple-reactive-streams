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

각 모듈은 독립된 브랜치로 관리됩니다.

> **중요: 브랜치는 참고용입니다!**
> 
> 각 브랜치는 제가 학습한 순서와 과정을 기록한 것일 뿐입니다.
> **브랜치를 참고하지 않고 직접 구현할수록 더 깊이 있는 학습이 됩니다.**
> 
> - 막히더라도 먼저 스스로 고민해보세요
> - Reactive Streams 스펙 문서를 직접 읽어보세요
> - 정말 막힐 때만 브랜치를 참고하세요

### 추천 학습 방법

```bash
# 1. main 브랜치에서 시작 (완성된 코드)
git checkout main

# 2. docs/ 폴더의 각 모듈 문서를 읽고 직접 구현해보기
#    (완성된 코드는 참고하지 않고!)

# 3. 정말 막힐 때만 해당 모듈 브랜치 참고
git diff main module-2-publisher  # 차이점 확인
```

### 브랜치 활용 (참고용)

```bash
# 특정 모듈의 구현 과정이 궁금할 때
git checkout module-2-publisher
git log --oneline

# 두 모듈 간의 변경사항 확인
git diff module-2-publisher module-3-backpressure

# 특정 파일의 변화 추적
git log -p --follow -- src/main/java/io/simplereactive/publisher/ArrayPublisher.java
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

`docs/` 폴더의 모듈 문서를 순서대로 읽고, **직접 구현**해보세요!

```bash
# docs/module-0-intro/README.md 부터 시작
# 완성된 코드를 보지 않고 직접 구현하는 것이 핵심입니다
```

> **Tip**: 막히면 Reactive Streams 스펙을 먼저 읽어보세요.
> 브랜치 참고는 최후의 수단으로!

## 학습 방법

### 핵심 원칙: 직접 구현하기

> **가장 효과적인 학습은 코드를 직접 작성하는 것입니다.**
> 
> 완성된 코드를 읽는 것만으로는 Reactive Streams의 복잡한 규약을 이해하기 어렵습니다.
> 직접 구현하고, 테스트를 실패시키고, 디버깅하는 과정에서 진정한 이해가 생깁니다.

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

1. **문서 읽기**: `docs/module-N-xxx/README.md`에서 개념 학습
2. **직접 구현**: 문서를 보고 코드를 직접 작성 (완성된 코드 참고 X)
3. **테스트 실행**: `./gradlew test`로 구현 검증
4. **디버깅**: 실패한 테스트를 분석하고 수정
5. **심화 학습**: 실제 Reactor/RxJava 코드와 비교

> **막혔을 때만** 해당 모듈 브랜치를 참고하세요.
> 스스로 해결할수록 더 깊이 있는 학습이 됩니다!

## 프로젝트 구조

```
simple-reactive-streams/
├── simple-reactive-core/           # 핵심 라이브러리
│   └── src/main/java/io/simplereactive/
│       ├── core/           # Publisher, Subscriber, Subscription, Processor, Flux
│       ├── publisher/      # ArrayPublisher, RangePublisher, DeferPublisher, HotPublisher
│       ├── subscriber/     # BufferedSubscriber, OverflowStrategy
│       ├── subscription/   # BaseSubscription, ArraySubscription, BufferedSubscription
│       ├── operator/       # Map, Filter, Take, Zip, OnErrorResume, SubscribeOn, PublishOn
│       ├── scheduler/      # Scheduler, Schedulers, SingleThread, Parallel
│       └── test/           # TestSubscriber
│
├── simple-reactive-example/        # Module 9 실전 예제
│   └── src/main/java/io/simplereactive/example/
│       ├── Product, Review, Inventory, ProductDetail  # 도메인 모델
│       ├── MockApis                                    # Mock API
│       ├── LegacyProductService                        # Thread/Future 기반 (비교용)
│       └── ReactiveProductService                      # Reactive 방식
│
└── docs/                           # PBL 학습 문서 (Module 0-9)
```

## 명령어

```bash
# 전체 빌드
./gradlew build

# core 모듈 테스트
./gradlew :simple-reactive-core:test

# example 모듈 테스트
./gradlew :simple-reactive-example:test

# TCK 테스트 (규약 검증)
./gradlew :simple-reactive-core:tckTest

# 특정 테스트
./gradlew :simple-reactive-core:test --tests "io.simplereactive.publisher.*"
```

## Tech Stack

- **Language**: Java 25
- **Build**: Gradle 8.14 (Kotlin DSL) - Multi-module
- **Testing**: JUnit 6 + AssertJ + Reactive Streams TCK
- **Package**: io.simplereactive

## 참고 자료

- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Project Reactor](https://projectreactor.io/)
- [RxJava](https://github.com/ReactiveX/RxJava)

## License

MIT License
