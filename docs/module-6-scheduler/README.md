# Module 6: Scheduler와 비동기

> ⏱️ 예상 시간: 2.5시간 | ★★★★★ 난이도

## 학습 목표

- Scheduler가 왜 필요한지 이해
- subscribeOn vs publishOn 차이 학습
- 스레드 경계 관리 구현

---

## 1. 문제 제시

### 시나리오

```java
// 현재 상황: 모두 main 스레드에서 실행
publisher
    .map(x -> heavyComputation(x))  // CPU 집약적
    .map(x -> saveToDatabase(x))    // I/O 블로킹
    .subscribe(subscriber);
```

**문제**: 모든 작업이 같은 스레드에서 실행되어 블로킹됨

---

## 2. 개념 설명

### Scheduler란?

작업을 **어떤 스레드에서 실행할지** 결정합니다.

```
┌─────────────────────────────────────────────────────┐
│  Scheduler 종류                                     │
├─────────────────────────────────────────────────────┤
│  ImmediateScheduler  : 현재 스레드에서 즉시 실행      │
│  SingleScheduler     : 단일 스레드에서 실행          │
│  ThreadPoolScheduler : 스레드 풀에서 실행            │
└─────────────────────────────────────────────────────┘
```

### subscribeOn vs publishOn

```
subscribeOn: 구독이 시작되는 스레드 지정 (위쪽에 영향)
publishOn:   이후 시그널이 발행되는 스레드 지정 (아래쪽에 영향)

┌─────────────────────────────────────────────────────┐
│                                                     │
│  source                                             │
│    │                                                │
│    │ subscribeOn(A)  ← 여기서부터 Thread A          │
│    │                                                │
│    ├── map()         ← Thread A                    │
│    │                                                │
│    │ publishOn(B)    ← 여기서부터 Thread B          │
│    │                                                │
│    ├── filter()      ← Thread B                    │
│    │                                                │
│    └── subscriber    ← Thread B                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 시각화

```
                    subscribeOn(Scheduler A)
                           ↓
┌──────────────────────────────────────────────────────┐
│  Thread A                                            │
│  ┌─────────┐    ┌─────────┐                         │
│  │ Source  │───→│   Map   │──┐                      │
│  └─────────┘    └─────────┘  │                      │
└──────────────────────────────│──────────────────────┘
                               │
                    publishOn(Scheduler B)
                               │
┌──────────────────────────────│──────────────────────┐
│  Thread B                    │                      │
│                    ┌─────────↓─┐    ┌────────────┐  │
│                    │  Filter   │───→│ Subscriber │  │
│                    └───────────┘    └────────────┘  │
└─────────────────────────────────────────────────────┘
```

---

## 3. 구현 가이드

### Step 1: Scheduler 인터페이스

```java
package io.simplereactive.scheduler;

/**
 * 작업 실행을 위한 스케줄러.
 */
public interface Scheduler {
    
    /**
     * 작업을 스케줄합니다.
     */
    void schedule(Runnable task);
    
    /**
     * 스케줄러를 종료합니다.
     */
    void shutdown();
}
```

### Step 2: ImmediateScheduler

```java
package io.simplereactive.scheduler;

/**
 * 현재 스레드에서 즉시 실행하는 Scheduler.
 */
public class ImmediateScheduler implements Scheduler {
    
    public static final ImmediateScheduler INSTANCE = new ImmediateScheduler();
    
    private ImmediateScheduler() {}
    
    @Override
    public void schedule(Runnable task) {
        task.run();  // 즉시 실행
    }
    
    @Override
    public void shutdown() {
        // 아무것도 안 함
    }
}
```

### Step 3: ThreadPoolScheduler

```java
package io.simplereactive.scheduler;

import java.util.concurrent.*;

/**
 * 스레드 풀에서 실행하는 Scheduler.
 */
public class ThreadPoolScheduler implements Scheduler {
    
    private final ExecutorService executor;
    
    public ThreadPoolScheduler(int poolSize) {
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("reactive-pool-" + t.getId());
            return t;
        });
    }
    
    @Override
    public void schedule(Runnable task) {
        executor.submit(task);
    }
    
    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
```

### Step 4: SubscribeOnOperator

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import io.simplereactive.scheduler.Scheduler;

/**
 * 구독을 지정된 Scheduler에서 실행하는 Operator.
 */
public class SubscribeOnOperator<T> implements Publisher<T> {
    
    private final Publisher<T> upstream;
    private final Scheduler scheduler;
    
    public SubscribeOnOperator(Publisher<T> upstream, Scheduler scheduler) {
        this.upstream = upstream;
        this.scheduler = scheduler;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // 구독 자체를 다른 스레드에서 실행
        scheduler.schedule(() -> upstream.subscribe(subscriber));
    }
}
```

### Step 5: PublishOnOperator

```java
package io.simplereactive.operator;

import io.simplereactive.core.*;
import io.simplereactive.scheduler.Scheduler;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 이후 시그널을 지정된 Scheduler에서 발행하는 Operator.
 */
public class PublishOnOperator<T> implements Publisher<T> {
    
    private final Publisher<T> upstream;
    private final Scheduler scheduler;
    
    public PublishOnOperator(Publisher<T> upstream, Scheduler scheduler) {
        this.upstream = upstream;
        this.scheduler = scheduler;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        upstream.subscribe(new PublishOnSubscriber<>(downstream, scheduler));
    }
    
    static class PublishOnSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> downstream;
        private final Scheduler scheduler;
        private final Queue<T> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger wip = new AtomicInteger(0);
        private volatile boolean done = false;
        private volatile Throwable error;
        private Subscription upstream;
        
        PublishOnSubscriber(Subscriber<? super T> downstream, Scheduler scheduler) {
            this.downstream = downstream;
            this.scheduler = scheduler;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            downstream.onSubscribe(s);
        }
        
        @Override
        public void onNext(T item) {
            queue.offer(item);
            scheduleIfNeeded();
        }
        
        @Override
        public void onError(Throwable t) {
            this.error = t;
            this.done = true;
            scheduleIfNeeded();
        }
        
        @Override
        public void onComplete() {
            this.done = true;
            scheduleIfNeeded();
        }
        
        private void scheduleIfNeeded() {
            if (wip.getAndIncrement() == 0) {
                scheduler.schedule(this::drain);
            }
        }
        
        private void drain() {
            int missed = 1;
            
            for (;;) {
                // 큐에서 꺼내서 downstream으로 전달
                T item;
                while ((item = queue.poll()) != null) {
                    downstream.onNext(item);
                }
                
                // 완료 체크
                if (done) {
                    if (error != null) {
                        downstream.onError(error);
                    } else {
                        downstream.onComplete();
                    }
                    return;
                }
                
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
    }
}
```

---

## 4. 사용 예시

```java
// I/O 스케줄러
Scheduler ioScheduler = new ThreadPoolScheduler(10);

// CPU 스케줄러
Scheduler computeScheduler = new ThreadPoolScheduler(
    Runtime.getRuntime().availableProcessors()
);

publisher
    .subscribeOn(ioScheduler)           // 구독은 IO 스레드에서
    .map(x -> fetchFromNetwork(x))      // IO 스레드
    .publishOn(computeScheduler)        // 이후는 Compute 스레드에서
    .map(x -> heavyComputation(x))      // Compute 스레드
    .publishOn(ioScheduler)             // 다시 IO 스레드로
    .subscribe(x -> saveToDb(x));       // IO 스레드
```

---

## 5. 체크포인트

- [ ] Scheduler 인터페이스 정의
- [ ] ImmediateScheduler 구현
- [ ] ThreadPoolScheduler 구현
- [ ] SubscribeOnOperator 구현
- [ ] PublishOnOperator 구현
- [ ] 스레드 전환 테스트 통과

```bash
/check 6
```

---

## 다음 단계

Module 7에서는 Hot vs Cold Publisher를 학습합니다.

```bash
/learn 7
```
