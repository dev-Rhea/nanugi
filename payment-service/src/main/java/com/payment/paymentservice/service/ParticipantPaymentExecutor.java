package com.payment.paymentservice.service;

import com.payment.common.lock.DistributedLock;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 참가자 단위 결제 호출을 별도 빈으로 분리한 이유: {@link DistributedLock}은 Spring AOP
 * 프록시를 거쳐야 동작하는데, GroupBuyPaymentService 내부에서 같은 클래스의 메서드를
 * this로 직접 호출(self-invocation)하면 프록시를 타지 않아 락이 걸리지 않는다.
 * 배치 결제(참가자별 CompletableFuture)와 단건 재시도가 같은 참가자를 동시에 건드리는
 * 상황을 직렬화하기 위해, 호출부를 별도 빈으로 떼어내 프록시 경유를 보장한다.
 */
@Component
@RequiredArgsConstructor
public class ParticipantPaymentExecutor {

    private final PaymentGateway paymentGateway;

    @DistributedLock(key = "'payment:' + #groupBuyId + ':' + #participantId")
    public PaymentResult executeParticipantPayment(Long groupBuyId, Long participantId,
                                                     String billingKey, String orderId,
                                                     BigDecimal amount, String orderName) {
        return paymentGateway.processPayment(billingKey, orderId, amount, orderName);
    }
}
