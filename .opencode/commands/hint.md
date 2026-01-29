---
description: 힌트 제공
agent: tutor
subtask: true
---

힌트 주제: $ARGUMENTS

막혔을 때 단계별로 힌트를 제공합니다. 직접 답을 주지 않고 스스로 해결할 수 있도록 유도합니다.

## 사용 예시

- `/hint request` - request(n) 구현 힌트
- `/hint backpressure` - Backpressure 구현 힌트
- `/hint state` - 상태 관리 힌트
- `/hint atomic` - Atomic 연산 힌트
- `/hint cancel` - cancel 처리 힌트
- `/hint onError` - 에러 처리 힌트

## 힌트 레벨

1. **Level 1**: 방향 제시 (어떤 개념을 알아야 하는지)
2. **Level 2**: 접근 방법 (어떻게 생각해야 하는지)
3. **Level 3**: 구체적 힌트 (코드 구조 제안)

같은 주제로 여러 번 요청하면 더 구체적인 힌트를 제공합니다.
