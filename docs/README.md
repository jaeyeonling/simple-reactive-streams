# Reactive Streams 학습 가이드

Reactive Streams 스펙을 직접 구현하며 학습하는 PBL(Problem-Based Learning) 프로젝트입니다.

## 학습 목표

- Reactive Streams의 4가지 핵심 인터페이스 이해 및 구현
- `request(n)` 기반 Backpressure 메커니즘 체험
- 39개 규약의 의미와 필요성 학습
- 실제 라이브러리(Reactor, RxJava)의 내부 동작 원리 이해

## 학습 로드맵

```
📚 Reactive Streams 완전 정복
═══════════════════════════════════════════════════════════════

Part I: 기초 다지기
─────────────────────────────────────────────────────────────
Module 0: 시작하기                    ⏱️ 30분   ★☆☆☆☆
Module 1: 핵심 인터페이스             ⏱️ 1시간  ★★☆☆☆
Module 2: 첫 번째 Publisher          ⏱️ 2시간  ★★★☆☆

Part II: 핵심 개념
─────────────────────────────────────────────────────────────
Module 3: Backpressure               ⏱️ 2시간  ★★★★☆
Module 4: Operators                  ⏱️ 3시간  ★★★★☆
Module 5: 에러 처리                  ⏱️ 1.5시간 ★★★☆☆

Part III: 고급 주제
─────────────────────────────────────────────────────────────
Module 6: Scheduler                  ⏱️ 2.5시간 ★★★★★
Module 7: Hot vs Cold               ⏱️ 1.5시간 ★★★☆☆

Part IV: 실전 적용
─────────────────────────────────────────────────────────────
Module 8: 레거시 리팩터링             ⏱️ 3~4시간 ★★★★★

═══════════════════════════════════════════════════════════════
📊 총 학습 시간: 약 17~18시간
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

### OpenCode 학습 도구 활용

```bash
# 모듈 학습 시작
/learn 1                    # Module 1 학습 시작

# 개념 질문
@tutor Backpressure가 뭐야?  # 튜터에게 질문

# 규약 확인
/spec 1.3                   # Rule 1.3 조회

# 구현 검증
/check 1                    # Module 1 구현 검증

# 막힐 때 힌트
/hint request               # request 구현 힌트

# 데이터 흐름 시각화
/visualize subscribe        # 구독 흐름 시각화
```

## 모듈별 개요

### Module 0: 시작하기
- 왜 Reactive가 필요한가?
- Reactive Streams 스펙 소개
- 개발 환경 설정

### Module 1: 핵심 인터페이스
- Publisher, Subscriber, Subscription, Processor 이해
- 4개의 인터페이스 직접 정의
- 시그널 흐름 이해

### Module 2: 첫 번째 Publisher
- ArrayPublisher 구현
- Subscription 상태 관리
- 기본 시그널 처리

### Module 3: Backpressure
- request(n)의 의미와 구현
- Demand 관리
- 다양한 Backpressure 전략

### Module 4: Operators
- Operator 패턴 이해
- map, filter, take 구현
- flatMap 도전

### Module 5: 에러 처리
- onError 규약
- 에러 전파 메커니즘
- 에러 복구 Operator

### Module 6: Scheduler
- 왜 Scheduler가 필요한가?
- subscribeOn vs publishOn
- 스레드 경계 관리

### Module 7: Hot vs Cold
- Cold Publisher 심화
- Hot Publisher 구현
- 실제 사용 사례

### Module 8: 실전 프로젝트
- Thread 기반 레거시 코드 분석
- Reactive로 리팩터링
- 성능 비교 및 평가

## 시작하기

```bash
# 1. 환경 확인
java -version    # Java 25 이상

# 2. 빌드 테스트
./gradlew build

# 3. 학습 시작
# OpenCode에서:
/learn 0
```

## 참고 자료

- [Reactive Streams Specification](https://www.reactive-streams.org/)
- [Project Reactor](https://projectreactor.io/)
- [RxJava](https://github.com/ReactiveX/RxJava)
