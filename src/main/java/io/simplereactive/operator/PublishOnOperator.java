package io.simplereactive.operator;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;
import io.simplereactive.core.Subscription;
import io.simplereactive.scheduler.Scheduler;
import io.simplereactive.scheduler.Worker;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 발행 시점의 스레드를 변경하는 Operator.
 *
 * <p>upstream에서 받은 시그널을 Scheduler에서 실행되도록 합니다.
 * 이후 연산자들은 해당 Scheduler 스레드에서 실행됩니다.
 *
 * <h2>동작 방식</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    PublishOnOperator                        │
 * ├─────────────────────────────────────────────────────────────┤
 * │                                                             │
 * │  Upstream Thread             Scheduler Thread               │
 * │  ──────────────              ────────────────               │
 * │                                                             │
 * │  onNext(1) ──> Queue ──────────> drain() ──> onNext(1)     │
 * │  onNext(2) ──> Queue ──────────> drain() ──> onNext(2)     │
 * │  onComplete ─> Queue ──────────> drain() ──> onComplete    │
 * │                                                             │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * [upstream thread]
 *     │
 * ──1──2──3──|
 *     │
 * publishOn(parallel)
 *     │
 *     │ ──────────────────────> [parallel-1 thread]
 *     │                              │
 *     │                         Queue → drain
 *     │                              │
 *     ▼                              ▼
 * downstream receives on [parallel-1 thread]: ──1──2──3──|
 * </pre>
 *
 * <h2>subscribeOn vs publishOn</h2>
 * <ul>
 *   <li>subscribeOn: 구독이 시작되는 스레드 결정 (위치 무관)</li>
 *   <li>publishOn: 이후 연산자들이 실행되는 스레드 결정 (위치 중요)</li>
 * </ul>
 *
 * <h2>내부 구현</h2>
 * <ul>
 *   <li>Queue를 사용하여 upstream 시그널을 버퍼링</li>
 *   <li>WIP(Work-In-Progress) 패턴으로 동시 drain 방지</li>
 *   <li>Scheduler의 Worker를 사용하여 순차 처리 보장</li>
 * </ul>
 *
 * <h2>학습 포인트</h2>
 * <ul>
 *   <li>publishOn 위치에 따라 이후 연산자들의 실행 스레드가 결정됨</li>
 *   <li>여러 publishOn을 사용하면 각각 적용됨</li>
 *   <li>CPU 집약적 작업 전에 parallel Scheduler로 전환</li>
 * </ul>
 *
 * @param <T> 요소 타입
 * @see SubscribeOnOperator
 */
public final class PublishOnOperator<T> implements Publisher<T> {

    private final Publisher<T> upstream;
    private final Scheduler scheduler;

    /**
     * PublishOnOperator를 생성합니다.
     *
     * @param upstream 원본 Publisher
     * @param scheduler 시그널을 발행할 Scheduler
     * @throws NullPointerException upstream 또는 scheduler가 null인 경우
     */
    public PublishOnOperator(Publisher<T> upstream, Scheduler scheduler) {
        this.upstream = Objects.requireNonNull(upstream, "Upstream must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Scheduler must not be null");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        
        Worker worker = scheduler.createWorker();
        upstream.subscribe(new PublishOnSubscriber<>(subscriber, worker));
    }

    /**
     * 시그널을 큐에 버퍼링하고 Scheduler에서 drain하는 Subscriber.
     *
     * <p>WIP 패턴을 사용하여 동시에 여러 drain이 실행되지 않도록 합니다.
     *
     * <h3>Reactive Streams 규약 준수</h3>
     * <ul>
     *   <li>Rule 1.7: 종료 후 시그널 차단</li>
     *   <li>Rule 2.12: 중복 onSubscribe 방지</li>
     *   <li>Rule 2.13: null 체크</li>
     *   <li>Rule 3.9: request(n<=0) 시 onError</li>
     * </ul>
     */
    private static final class PublishOnSubscriber<T> implements Subscriber<T>, Subscription, Runnable {

        private final Subscriber<? super T> downstream;
        private final Worker worker;
        private final Queue<T> queue;
        
        private final AtomicReference<Subscription> upstream = new AtomicReference<>();
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicInteger wip = new AtomicInteger(0);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        // error를 먼저 설정하고 completed를 나중에 설정 (메모리 가시성)
        private volatile Throwable error;
        private volatile boolean completed;

        PublishOnSubscriber(Subscriber<? super T> downstream, Worker worker) {
            this.downstream = downstream;
            this.worker = worker;
            this.queue = new ConcurrentLinkedQueue<>();
        }

        // ========== Subscriber 구현 ==========

        @Override
        public void onSubscribe(Subscription s) {
            // Rule 2.13: null 체크
            if (s == null) {
                throw new NullPointerException("Rule 2.13: Subscription must not be null");
            }
            
            // Rule 2.12: 중복 onSubscribe 방지
            if (upstream.compareAndSet(null, s)) {
                downstream.onSubscribe(this);
            } else {
                s.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            // Rule 2.13: null 체크
            if (item == null) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
                onError(new NullPointerException("Rule 2.13: onNext item must not be null"));
                return;
            }
            
            // Rule 1.7: 종료 후 시그널 차단
            if (done.get()) {
                return;
            }
            
            queue.offer(item);
            trySchedule();
        }

        @Override
        public void onError(Throwable t) {
            // Rule 2.13: null 체크
            if (t == null) {
                t = new NullPointerException("Rule 2.13: onError throwable must not be null");
            }
            
            // Rule 1.7: 종료 후 시그널 차단
            if (done.get()) {
                return;
            }
            
            // error를 먼저 설정 (메모리 가시성: completed 읽기 전에 error가 보임)
            error = t;
            completed = true;
            trySchedule();
        }

        @Override
        public void onComplete() {
            // Rule 1.7: 종료 후 시그널 차단
            if (done.get()) {
                return;
            }
            
            completed = true;
            trySchedule();
        }

        // ========== Subscription 구현 ==========

        @Override
        public void request(long n) {
            // Rule 3.9: request(n <= 0) 시 onError
            if (n <= 0) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
                onError(new IllegalArgumentException(
                        "Rule 3.9: Request must be positive, but was " + n));
                return;
            }
            
            // demand 추가 (overflow 방지)
            requested.getAndUpdate(current -> {
                long next = current + n;
                return next < 0 ? Long.MAX_VALUE : next;
            });
            
            // upstream에 요청 전달
            Subscription s = upstream.get();
            if (s != null) {
                s.request(n);
            }
            
            trySchedule();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Subscription s = upstream.get();
                if (s != null) {
                    s.cancel();
                }
                // 메모리 누수 방지: 큐에 남은 아이템 정리
                queue.clear();
                worker.dispose();
            }
        }

        // ========== Runnable 구현 (drain) ==========

        @Override
        public void run() {
            // WIP 패턴: 하나의 스레드만 drain 실행
            int missed = 1;

            for (;;) {
                // demand만큼 데이터 전달
                long r = requested.get();
                long e = 0;

                while (e != r) {
                    if (cancelled.get()) {
                        queue.clear();
                        return;
                    }

                    T item = queue.poll();
                    
                    if (item == null) {
                        // 큐가 비었으면 완료 상태 확인
                        if (checkTerminated()) {
                            return;
                        }
                        break;
                    }

                    // downstream.onNext 호출 (예외 발생 시 처리)
                    try {
                        downstream.onNext(item);
                    } catch (Throwable ex) {
                        // Rule 2.13: downstream에서 예외 발생 시 upstream 취소 후 에러 전파
                        // (규약상 downstream은 예외를 던지면 안 되지만 방어적 처리)
                        Subscription s = upstream.get();
                        if (s != null) {
                            s.cancel();
                        }
                        queue.clear();
                        if (done.compareAndSet(false, true)) {
                            downstream.onError(ex);
                        }
                        worker.dispose();
                        return;
                    }
                    e++;
                }

                // 전달한 만큼 demand 차감
                if (e != 0 && r != Long.MAX_VALUE) {
                    requested.addAndGet(-e);
                }

                // 완료 상태 확인 (demand 없이도)
                if (queue.isEmpty() && checkTerminated()) {
                    return;
                }

                // 더 처리할 작업이 있는지 확인
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }

        /**
         * 종료 상태를 확인하고 downstream에 시그널을 전달합니다.
         *
         * @return 종료되었으면 true
         */
        private boolean checkTerminated() {
            if (completed) {
                Throwable ex = error;
                if (ex != null) {
                    if (done.compareAndSet(false, true)) {
                        downstream.onError(ex);
                    }
                } else {
                    if (done.compareAndSet(false, true)) {
                        downstream.onComplete();
                    }
                }
                worker.dispose();
                return true;
            }
            return false;
        }

        /**
         * drain 스케줄링을 시도합니다.
         *
         * <p>WIP 패턴을 사용하여 이미 drain이 스케줄되어 있으면
         * 추가 스케줄링을 하지 않습니다.
         */
        private void trySchedule() {
            if (wip.getAndIncrement() == 0) {
                worker.schedule(this);
            }
        }
    }
}
