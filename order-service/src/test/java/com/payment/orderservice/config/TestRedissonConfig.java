package com.payment.orderservice.config;

import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * common.lock.RedissonConfig의 실제 빈은 생성 시점에 Redis로 연결을 시도한다.
 * 테스트 환경에는 Redis가 없으므로 목(mock)으로 대체한다.
 */
@TestConfiguration
public class TestRedissonConfig {

    @Bean
    @Primary
    public RedissonClient mockRedissonClient() {
        return Mockito.mock(RedissonClient.class);
    }
}
