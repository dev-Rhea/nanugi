package com.payment.common.lock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    // Redisson.create()는 생성 시점에 실제로 Redis에 연결한다. @Lazy로 미뤄두면
    // 테스트처럼 Redis가 없는 환경에서 목(mock) 빈으로 대체됐을 때 이 실제 빈이
    // 쓰이지 않는 한 연결을 시도하지 않는다.
    @Lazy
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
