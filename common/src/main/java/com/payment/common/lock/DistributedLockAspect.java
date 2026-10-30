package com.payment.common.lock;

import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * {@link DistributedLock}이 붙은 메서드를 가로채 Redisson 분산 락으로 감싼다.
 * 트랜잭션보다 먼저 락을 잡고, 트랜잭션이 끝난 뒤에야 락을 풀도록
 * {@code @Order(HIGHEST_PRECEDENCE)}로 가장 바깥쪽 advice가 되게 한다
 * (lock → 트랜잭션 시작 → commit → unlock 순서 보장).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAspect {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String key = LOCK_KEY_PREFIX + resolveKey(joinPoint, distributedLock.key());
        RLock rLock = redissonClient.getLock(key);

        boolean acquired;
        try {
            acquired = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(CustomErrorCode.LOCK_TIMEOUT, "Interrupted while waiting for lock: " + key);
        }

        if (!acquired) {
            log.warn("Failed to acquire distributed lock: key={}", key);
            throw new CustomException(CustomErrorCode.LOCK_TIMEOUT, "Failed to acquire lock: " + key);
        }

        log.debug("Distributed lock acquired: key={}", key);
        try {
            return joinPoint.proceed();
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("Distributed lock released: key={}", key);
            }
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        try {
            return parser.parseExpression(keyExpression).getValue(context, String.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve @DistributedLock key '%s' on %s".formatted(keyExpression, method), e);
        }
    }
}
