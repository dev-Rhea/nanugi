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

    // @annotation(distributedLock)로 애너테이션 값을 advice 파라미터에 바인딩하는
    // 방식은 argNames를 명시해도 "Required to bind 2 arguments, but only bound 1"
    // 예외가 나는 환경이 있었다(이 아티팩트 조합에서 직접 재현·확인함). Spring AOP의
    // 바인딩 메커니즘에 의존하지 않고, 매칭된 메서드에서 애너테이션을 직접 리플렉션으로
    // 읽어오는 방식으로 바꿔 그 의존성을 제거한다.
    @Around("@annotation(com.payment.common.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        DistributedLock distributedLock = signature.getMethod().getAnnotation(DistributedLock.class);

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
