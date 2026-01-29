---
description: Reactive Streams 규약 조회
agent: tutor
subtask: true
---

규약 번호: $ARGUMENTS

Reactive Streams 규약(39개 규칙)을 조회합니다.

## 사용 예시

- `/spec` - 전체 규약 목록 보기
- `/spec 1` - Publisher 규약 (Rule 1.x)
- `/spec 2` - Subscriber 규약 (Rule 2.x)
- `/spec 3` - Subscription 규약 (Rule 3.x)
- `/spec 4` - Processor 규약 (Rule 4.x)
- `/spec 1.3` - 특정 규칙 상세 보기

## 규약 구조

- **Publisher (1.01 ~ 1.11)**: 데이터 발행자 규약
- **Subscriber (2.01 ~ 2.13)**: 데이터 구독자 규약
- **Subscription (3.01 ~ 3.17)**: 구독 관계 규약
- **Processor (4.01 ~ 4.02)**: Publisher + Subscriber 규약

각 규칙에 대해 다음을 설명합니다:
1. 규칙 내용 (영문/한글)
2. 왜 이 규칙이 필요한지
3. 위반 시 발생하는 문제
4. 구현 예시

상세 정보는 `reactive-spec` 스킬을 참조하세요.
