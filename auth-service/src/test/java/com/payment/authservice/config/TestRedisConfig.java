package com.payment.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import redis.embedded.RedisServer;

@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

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
