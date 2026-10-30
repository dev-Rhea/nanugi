package com.payment.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service는 spring-boot-starter-web이 없어(Feign만 사용) Boot의
 * JacksonAutoConfiguration이 기본 ObjectMapper 빈을 만들어주지 않는다.
 * SettlementService가 ObjectMapper를 직접 주입받으므로 명시적으로 빈을 등록한다
 * (docker-compose로 실제 기동해보고 나서야 드러난 문제).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
