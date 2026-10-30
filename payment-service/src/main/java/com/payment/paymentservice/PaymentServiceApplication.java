package com.payment.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// common 모듈의 공용 컴포넌트(분산 락 Aspect, 암복호화 Converter, 전역 예외 처리 등)는
// com.payment.paymentservice 패키지의 하위가 아니므로 명시적으로 스캔 범위를 넓혀야 한다.
@SpringBootApplication
@ComponentScan(basePackages = {"com.payment"})
@EntityScan(basePackages = {"com.payment"})
@EnableJpaRepositories(basePackages = {"com.payment"})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
