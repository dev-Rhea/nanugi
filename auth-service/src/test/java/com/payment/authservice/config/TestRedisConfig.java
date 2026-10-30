package com.payment.authservice.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;

    // ObjectMapper는 메인 코드의 JacksonConfig가 제공하는 빈을 그대로 쓴다.
    // 여기서 같은 이름으로 다시 정의하면 빈 이름 충돌로 컨텍스트가 기동되지
    // 않는다(JacksonConfig 추가 후 실제로 확인됨).

    @PostConstruct
    public void startRedis() {
        try {
            redisServer = new RedisServer(6379);
            redisServer.start();
        } catch (Exception e) {
            // Redis already running or port in use
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }
}
