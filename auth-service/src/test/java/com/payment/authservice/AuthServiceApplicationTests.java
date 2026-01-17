package com.payment.authservice;

import com.payment.authservice.config.TestRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestRedisConfig.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
