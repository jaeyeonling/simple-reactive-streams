package io.simplereactive.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Core 인터페이스 정의 테스트.
 * 
 * Module 1의 학습 검증을 위한 테스트입니다.
 */
class CoreInterfaceTest {
    
    @Test
    @DisplayName("Publisher 인터페이스가 subscribe 메서드를 가지고 있다")
    void publisherHasSubscribeMethod() throws NoSuchMethodException {
        var method = Publisher.class.getMethod("subscribe", Subscriber.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }
    
    @Test
    @DisplayName("Subscriber 인터페이스가 4개의 시그널 메서드를 가지고 있다")
    void subscriberHasSignalMethods() throws NoSuchMethodException {
        assertThat(Subscriber.class.getMethod("onSubscribe", Subscription.class)).isNotNull();
        assertThat(Subscriber.class.getMethod("onNext", Object.class)).isNotNull();
        assertThat(Subscriber.class.getMethod("onError", Throwable.class)).isNotNull();
        assertThat(Subscriber.class.getMethod("onComplete")).isNotNull();
    }
    
    @Test
    @DisplayName("Subscription 인터페이스가 request와 cancel을 가지고 있다")
    void subscriptionHasRequestAndCancel() throws NoSuchMethodException {
        assertThat(Subscription.class.getMethod("request", long.class)).isNotNull();
        assertThat(Subscription.class.getMethod("cancel")).isNotNull();
    }
    
    @Test
    @DisplayName("Processor는 Publisher이자 Subscriber이다")
    void processorExtendsPublisherAndSubscriber() {
        assertThat(Publisher.class.isAssignableFrom(Processor.class)).isTrue();
        assertThat(Subscriber.class.isAssignableFrom(Processor.class)).isTrue();
    }
}
