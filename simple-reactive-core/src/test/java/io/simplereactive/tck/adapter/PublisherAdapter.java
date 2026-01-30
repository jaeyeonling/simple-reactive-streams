package io.simplereactive.tck.adapter;

import io.simplereactive.core.Publisher;
import io.simplereactive.core.Subscriber;

/**
 * 우리의 Publisher를 org.reactivestreams.Publisher로 어댑팅합니다.
 *
 * <p>TCK는 {@link org.reactivestreams.Publisher}를 기대하므로,
 * 우리가 구현한 {@link Publisher}를 래핑하여 TCK에서 테스트할 수 있게 합니다.
 *
 * <h2>어댑터 패턴</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  TCK                                                        │
 * │    │                                                        │
 * │    ▼                                                        │
 * │  org.reactivestreams.Publisher                              │
 * │    │                                                        │
 * │    ▼                                                        │
 * │  PublisherAdapter (이 클래스)                                │
 * │    │                                                        │
 * │    ▼                                                        │
 * │  io.simplereactive.core.Publisher (우리 구현)                │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @param <T> 발행 요소 타입
 */
public class PublisherAdapter<T> implements org.reactivestreams.Publisher<T> {

    private final Publisher<T> delegate;

    /**
     * 우리의 Publisher를 래핑합니다.
     *
     * @param delegate 우리가 구현한 Publisher
     */
    public PublisherAdapter(Publisher<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void subscribe(org.reactivestreams.Subscriber<? super T> subscriber) {
        // org.reactivestreams.Subscriber를 우리의 Subscriber로 어댑팅
        delegate.subscribe(new SubscriberAdapter<>(subscriber));
    }

    /**
     * org.reactivestreams.Subscriber를 우리의 Subscriber로 어댑팅합니다.
     */
    private static class SubscriberAdapter<T> implements Subscriber<T> {

        private final org.reactivestreams.Subscriber<? super T> delegate;

        SubscriberAdapter(org.reactivestreams.Subscriber<? super T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(io.simplereactive.core.Subscription subscription) {
            // 우리의 Subscription을 org.reactivestreams.Subscription으로 어댑팅
            delegate.onSubscribe(new SubscriptionAdapter(subscription));
        }

        @Override
        public void onNext(T item) {
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }

    /**
     * 우리의 Subscription을 org.reactivestreams.Subscription으로 어댑팅합니다.
     */
    private static class SubscriptionAdapter implements org.reactivestreams.Subscription {

        private final io.simplereactive.core.Subscription delegate;

        SubscriptionAdapter(io.simplereactive.core.Subscription delegate) {
            this.delegate = delegate;
        }

        @Override
        public void request(long n) {
            delegate.request(n);
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }
    }
}
