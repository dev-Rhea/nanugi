package com.payment.orderservice.consumer;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyStatus;
import com.payment.orderservice.event.OrderEventPublisher;
import com.payment.orderservice.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final GroupBuyRepository groupBuyRepository;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    @KafkaListener(topics = "group-buy.payment.success", groupId = "order-service-group")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("Received PaymentSuccessEvent: groupBuyId={}", event.groupBuyId());

        groupBuyRepository.findByIdWithParticipants(event.groupBuyId())
            .ifPresentOrElse(
                groupBuy -> {
                    if (groupBuy.getStatus() != GroupBuyStatus.CONFIRMED) {
                        log.warn("GroupBuy {} is not in CONFIRMED status, current={}",
                            groupBuy.getId(), groupBuy.getStatus());
                        return;
                    }

                    groupBuy.updateStatus(GroupBuyStatus.PAID);
                    log.info("GroupBuy {} status updated to PAID", groupBuy.getId());

                    // 분석 서비스용 공동구매 완료 이벤트 발행
                    eventPublisher.publishGroupBuyCompleted(groupBuy);
                },
                () -> log.error("GroupBuy not found: id={}", event.groupBuyId())
            );
    }

    public record PaymentSuccessEvent(Long groupBuyId, LocalDateTime completedAt) {}
}
