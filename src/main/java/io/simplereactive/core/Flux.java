package io.simplereactive.core;

import io.simplereactive.operator.FilterOperator;
import io.simplereactive.operator.MapOperator;
import io.simplereactive.operator.OnErrorResumeOperator;
import io.simplereactive.operator.OnErrorReturnOperator;
import io.simplereactive.operator.PublishOnOperator;
import io.simplereactive.operator.SubscribeOnOperator;
import io.simplereactive.operator.TakeOperator;
import io.simplereactive.publisher.ArrayPublisher;
import io.simplereactive.publisher.EmptyPublisher;
import io.simplereactive.publisher.ErrorPublisher;
import io.simplereactive.publisher.RangePublisher;
import io.simplereactive.scheduler.Scheduler;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Operator 체이닝을 지원하는 Publisher 래퍼.
 *
 * <p>기존 Publisher를 감싸서 map, filter, take 등의 연산을
 * 메서드 체이닝으로 사용할 수 있게 합니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * Flux.just(1, 2, 3, 4, 5)
 *     .map(x -> x * 2)
 *     .filter(x -> x > 5)
 *     .take(2)
 *     .subscribe(subscriber);
 * }</pre>
 *
 * <h2>Marble Diagram</h2>
 * <pre>
 * Source:   ──1──2──3──4──5──|
 *               │
 *          map(x*2)
 *               │
 *           ──2──4──6──8──10─|
 *               │
 *         filter(>5)
 *               │
 *           ─────────6──8──10─|
 *               │
 *           take(2)
 *               │
 *           ─────────6──8──|
 * </pre>
 *
 * @param <T> 요소 타입
 * @see ArrayPublisher
 * @see EmptyPublisher
 * @see RangePublisher
 * @see MapOperator
 * @see FilterOperator
 * @see TakeOperator
 * @see OnErrorResumeOperator
 * @see OnErrorReturnOperator
 * @see SubscribeOnOperator
 * @see PublishOnOperator
 */
public class Flux<T> implements Publisher<T> {

    private final Publisher<T> source;

    // ========== 생성자 ==========

    /**
     * 기존 Publisher를 감싸는 Flux를 생성합니다.
     *
     * @param source 원본 Publisher
     */
    protected Flux(Publisher<T> source) {
        this.source = Objects.requireNonNull(source, "Source must not be null");
    }

    // ========== 팩토리 메서드 ==========

    /**
     * 기존 Publisher를 Flux로 변환합니다.
     *
     * @param publisher 원본 Publisher
     * @param <T> 요소 타입
     * @return Flux 인스턴스
     */
    public static <T> Flux<T> from(Publisher<T> publisher) {
        if (publisher instanceof Flux) {
            return (Flux<T>) publisher;
        }
        return new Flux<>(publisher);
    }

    /**
     * 주어진 요소들로 Flux를 생성합니다.
     *
     * @param items 발행할 요소들
     * @param <T> 요소 타입
     * @return Flux 인스턴스
     * @throws NullPointerException items가 null이거나 null 요소를 포함하는 경우
     * @see ArrayPublisher
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Flux<T> just(T... items) {
        Objects.requireNonNull(items, "Items must not be null");
        return new Flux<>(new ArrayPublisher<>(items));
    }

    /**
     * 비어있는 Flux를 생성합니다.
     *
     * <p>구독 시 즉시 onComplete를 호출합니다.
     *
     * @param <T> 요소 타입
     * @return 빈 Flux
     * @see EmptyPublisher
     */
    public static <T> Flux<T> empty() {
        return new Flux<>(EmptyPublisher.instance());
    }

    /**
     * 정수 범위로 Flux를 생성합니다.
     *
     * <p>start부터 시작하여 count개의 연속된 정수를 발행합니다.
     *
     * @param start 시작값 (포함)
     * @param count 개수
     * @return 범위 Flux
     * @see RangePublisher
     */
    public static Flux<Integer> range(int start, int count) {
        if (count <= 0) {
            return empty();
        }
        return new Flux<>(new RangePublisher(start, count));
    }

    /**
     * 즉시 에러를 발행하는 Flux를 생성합니다.
     *
     * <p>구독 시 즉시 onError를 호출합니다.
     *
     * <pre>
     * ──✗  (즉시 에러)
     * </pre>
     *
     * @param error 발행할 에러
     * @param <T> 요소 타입
     * @return 에러 Flux
     * @see ErrorPublisher
     */
    public static <T> Flux<T> error(Throwable error) {
        return new Flux<>(new ErrorPublisher<>(error));
    }

    // ========== Operator 메서드 ==========

    /**
     * 각 요소를 변환합니다.
     *
     * <pre>
     * ──1──2──3──|
     *      │
     * map(x -> x * 2)
     *      │
     * ──2──4──6──|
     * </pre>
     *
     * @param mapper 변환 함수
     * @param <R> 결과 타입
     * @return 변환된 Flux
     * @see MapOperator
     */
    public <R> Flux<R> map(Function<? super T, ? extends R> mapper) {
        return new Flux<>(new MapOperator<>(this, mapper));
    }

    /**
     * 조건에 맞는 요소만 통과시킵니다.
     *
     * <pre>
     * ──1──2──3──4──5──|
     *        │
     * filter(x -> x % 2 == 0)
     *        │
     * ─────2─────4─────|
     * </pre>
     *
     * @param predicate 필터 조건
     * @return 필터링된 Flux
     * @see FilterOperator
     */
    public Flux<T> filter(Predicate<? super T> predicate) {
        return new Flux<>(new FilterOperator<>(this, predicate));
    }

    /**
     * 처음 n개의 요소만 가져옵니다.
     *
     * <pre>
     * ──1──2──3──4──5──|
     *        │
     *    take(3)
     *        │
     * ──1──2──3──|
     * </pre>
     *
     * @param n 가져올 개수
     * @return 제한된 Flux
     * @see TakeOperator
     */
    public Flux<T> take(long n) {
        return new Flux<>(new TakeOperator<>(this, n));
    }

    // ========== 에러 처리 메서드 ==========

    /**
     * 에러 발생 시 대체 Publisher로 전환합니다.
     *
     * <pre>
     * ──1──2──✗
     *        │
     *   onErrorResume(e -> fallback)
     *        │
     * ──1──2──3──4──|
     * </pre>
     *
     * @param fallback 에러 발생 시 대체 Publisher를 반환하는 함수
     * @return 에러 복구 Flux
     * @see OnErrorResumeOperator
     */
    public Flux<T> onErrorResume(Function<? super Throwable, ? extends Publisher<T>> fallback) {
        return new Flux<>(new OnErrorResumeOperator<>(this, fallback));
    }

    /**
     * 에러 발생 시 기본값을 반환합니다.
     *
     * <pre>
     * ──1──2──✗
     *        │
     *   onErrorReturn(e -> -1)
     *        │
     * ──1──2──(-1)──|
     * </pre>
     *
     * @param fallback 에러 발생 시 기본값을 반환하는 함수
     * @return 에러 복구 Flux
     * @see OnErrorReturnOperator
     */
    public Flux<T> onErrorReturn(Function<? super Throwable, ? extends T> fallback) {
        return new Flux<>(new OnErrorReturnOperator<>(this, fallback));
    }

    /**
     * 에러 발생 시 고정 기본값을 반환합니다.
     *
     * <pre>
     * ──1──2──✗
     *        │
     *   onErrorReturn(-1)
     *        │
     * ──1──2──(-1)──|
     * </pre>
     *
     * @param defaultValue 에러 발생 시 반환할 기본값
     * @return 에러 복구 Flux
     * @see OnErrorReturnOperator
     */
    public Flux<T> onErrorReturn(T defaultValue) {
        return new Flux<>(new OnErrorReturnOperator<>(this, defaultValue));
    }

    // ========== Scheduler 메서드 ==========

    /**
     * 구독 시점의 스레드를 변경합니다.
     *
     * <p>subscribe() 호출이 지정된 Scheduler에서 실행됩니다.
     * 결과적으로 upstream의 데이터 생성도 해당 Scheduler에서 실행됩니다.
     *
     * <pre>
     * [main thread]
     *      │
     * subscribeOn(single)
     *      │
     *      │ ───────────────> [single-1 thread]
     *      │                       │
     *      │                  upstream.subscribe()
     *      │                       │
     *      │                  ──1──2──3──|
     *      │                       │
     *      ▼                       ▼
     * downstream receives on [single-1 thread]
     * </pre>
     *
     * <h3>subscribeOn vs publishOn</h3>
     * <ul>
     *   <li>subscribeOn: 구독이 시작되는 스레드 결정 (위치 무관)</li>
     *   <li>publishOn: 이후 연산자들이 실행되는 스레드 결정 (위치 중요)</li>
     * </ul>
     *
     * @param scheduler 구독을 실행할 Scheduler
     * @return Scheduler가 적용된 Flux
     * @see SubscribeOnOperator
     */
    public Flux<T> subscribeOn(Scheduler scheduler) {
        return new Flux<>(new SubscribeOnOperator<>(this, scheduler));
    }

    /**
     * 발행 시점의 스레드를 변경합니다.
     *
     * <p>upstream에서 받은 시그널이 지정된 Scheduler에서 전달됩니다.
     * publishOn 이후의 연산자들은 해당 Scheduler에서 실행됩니다.
     *
     * <pre>
     * [upstream thread]
     *      │
     * ──1──2──3──|
     *      │
     * publishOn(parallel)
     *      │
     *      │ ───────────────> [parallel-1 thread]
     *      │                       │
     *      │                  Queue → drain
     *      │                       │
     *      ▼                       ▼
     * downstream receives on [parallel-1 thread]
     * </pre>
     *
     * <h3>subscribeOn vs publishOn</h3>
     * <ul>
     *   <li>subscribeOn: 구독이 시작되는 스레드 결정 (위치 무관)</li>
     *   <li>publishOn: 이후 연산자들이 실행되는 스레드 결정 (위치 중요)</li>
     * </ul>
     *
     * @param scheduler 시그널을 발행할 Scheduler
     * @return Scheduler가 적용된 Flux
     * @see PublishOnOperator
     */
    public Flux<T> publishOn(Scheduler scheduler) {
        return new Flux<>(new PublishOnOperator<>(this, scheduler));
    }

    // ========== Hot Publisher 변환 ==========

    /**
     * Cold Publisher를 ConnectableFlux로 변환합니다.
     *
     * <p>connect()를 호출하기 전까지는 upstream에 구독하지 않으며,
     * connect() 호출 시 모든 구독자에게 동시에 데이터가 발행됩니다.
     *
     * <pre>
     * Cold Publisher          ConnectableFlux
     * ─────────────          ─────────────────
     * 구독마다 새로 시작  →   connect() 시 한 번만 시작
     *                        모든 구독자에게 멀티캐스트
     * </pre>
     *
     * <h3>사용 예시</h3>
     * <pre>{@code
     * ConnectableFlux<Integer> hot = Flux.range(1, 5).publish();
     *
     * // 구독자 등록 (아직 데이터 안 받음)
     * hot.subscribe(subscriberA);
     * hot.subscribe(subscriberB);
     *
     * // connect() 호출 시 모든 구독자에게 동시 발행
     * hot.connect();
     * }</pre>
     *
     * @return 수동 연결 가능한 ConnectableFlux
     * @see ConnectableFlux
     * @see #share()
     */
    public ConnectableFlux<T> publish() {
        return new ConnectableFlux<>(this);
    }

    /**
     * Cold Publisher를 자동 연결 Hot Publisher로 변환합니다.
     *
     * <p>첫 번째 구독자가 등록될 때 자동으로 upstream에 연결됩니다.
     * 이후 구독자들은 진행 중인 스트림에 합류합니다.
     *
     * <pre>
     * Cold Publisher        share()
     * ─────────────        ───────
     * A 구독 → 1,2,3,4,5   A 구독 → 1,2,3,4,5
     * B 구독 → 1,2,3,4,5   B 구독 → (놓침),3,4,5  ← 늦게 구독하면 이전 데이터 놓침
     * </pre>
     *
     * <h3>사용 예시</h3>
     * <pre>{@code
     * Flux<Integer> shared = Flux.range(1, 5).share();
     *
     * // 첫 구독자 - 자동 연결
     * shared.subscribe(subscriberA);  // 1, 2, 3, 4, 5 수신
     *
     * // 늦은 구독자 - 진행 중인 스트림 합류
     * shared.subscribe(subscriberB);  // 이미 발행된 데이터는 놓침
     * }</pre>
     *
     * <h3>publish().autoConnect()와 동일</h3>
     * <pre>{@code
     * // 아래 두 코드는 동일
     * flux.share()
     * flux.publish().autoConnect()
     * }</pre>
     *
     * @return 자동 연결 Hot Publisher
     * @see #publish()
     * @see ConnectableFlux#autoConnect()
     */
    public Flux<T> share() {
        return publish().autoConnect();
    }

    // ========== Publisher 구현 ==========

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException Rule 1.9 - subscriber가 null인 경우
     */
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        // Rule 1.9: null 체크를 명시적으로 수행하여 명확한 에러 메시지 제공
        if (subscriber == null) {
            throw new NullPointerException("Rule 1.9: Subscriber must not be null");
        }
        source.subscribe(subscriber);
    }
}
