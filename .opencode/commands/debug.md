---
description: 버그 원인 분석
agent: debugger
subtask: true
---

디버그 대상: $ARGUMENTS

에러 메시지나 실패하는 테스트를 분석합니다.

사용 예시:
- `/debug` - 최근 테스트 실패 분석
- `/debug "IllegalStateException"` - 특정 예외 분석
- `/debug ArrayPublisherTest` - 특정 테스트 실패 분석

Reactive Streams 규약 위반으로 인한 버그를 특히 잘 찾아냅니다.
