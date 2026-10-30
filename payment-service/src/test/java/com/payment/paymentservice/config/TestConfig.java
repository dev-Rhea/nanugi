package com.payment.paymentservice.config;

import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

@TestConfiguration
public class TestConfig {

    // 메서드명을 메인 설정(KafkaConfig/RedisConfig)의 빈 이름과 다르게 지어
    // 빈 이름 충돌(BeanDefinitionOverrideException)을 피하고, @Primary로
    // 타입 기준 주입 시 이 목(mock) 빈이 선택되게 한다. 이전에는 이름이 같아서
    // 컨텍스트 자체가 기동되지 않는 상태였다(수정 전 확인됨).

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> mockKafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }

    @Bean
    @Primary
    public RedisConnectionFactory mockRedisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> mockRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    // RedissonConfig(common)의 실제 빈은 생성 시점에 Redis로 연결을 시도하므로
    // 컨텍스트 로딩 테스트에서는 목으로 대체한다. 분산 락 동작 자체는
    // DistributedLockAspectTest(Testcontainers 실제 Redis)로 별도 검증한다.
    @Bean
    @Primary
    public RedissonClient mockRedissonClient() {
        return Mockito.mock(RedissonClient.class);
    }

    // ObjectMapper는 메인 코드의 JacksonConfig가 제공하는 빈(JavaTimeModule 등록)을
    // 그대로 쓴다. 이 클래스에서 같은 이름으로 다시 정의하면 빈 이름 충돌로
    // ApplicationContext가 기동 자체를 못 하는 상태였다(수정 전 확인됨).

    @Bean
    @Primary
    public RetryTemplate mockKafkaRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(100)
            .build();
    }
}
