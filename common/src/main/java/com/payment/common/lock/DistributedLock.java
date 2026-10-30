package com.payment.common.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redis(Redisson) 기반 분산 락을 메서드 단위로 선언적으로 적용한다.
 * key는 메서드 파라미터 이름을 변수로 쓰는 SpEL 표현식이다. 예: "'payment:' + #groupBuyId"
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DistributedLock {

    String key();

    long waitTime() default 5L;

    long leaseTime() default 10L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
