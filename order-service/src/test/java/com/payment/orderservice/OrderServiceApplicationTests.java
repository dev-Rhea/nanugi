package com.payment.orderservice;

import com.payment.orderservice.config.TestRedissonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedissonConfig.class)
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
