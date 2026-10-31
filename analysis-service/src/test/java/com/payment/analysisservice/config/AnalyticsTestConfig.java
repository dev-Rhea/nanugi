package com.payment.analysisservice.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

@TestConfiguration
public class AnalyticsTestConfig {

    @Bean
    @Primary
    public KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry() {
        return Mockito.mock(KafkaListenerEndpointRegistry.class);
    }
}
