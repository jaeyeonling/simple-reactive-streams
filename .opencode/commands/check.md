---
description: 구현 검증
agent: checker
subtask: true
---

검증 대상: $ARGUMENTS

학습자의 구현이 Reactive Streams 규약을 준수하는지 검증합니다.

## 사용 예시

- `/check` - 현재 변경 사항 검증
- `/check 1` - Module 1 구현 검증
- `/check 2` - Module 2 구현 검증
- `/check ArrayPublisher` - 특정 클래스 검증
- `/check tck` - TCK 테스트로 전체 규약 검증

## 검증 항목

1. **규약 준수**: 39개 Reactive Streams 규칙
2. **테스트 통과**: 단위 테스트 및 TCK 테스트
3. **코드 품질**: 상태 관리, 동시성 안전성

검증 통과 시 다음 모듈로 진행할 수 있습니다.
