package com.payment.paymentservice.service;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.group-buy-payment-success:group-buy-payment-success}")
    private String paymentSuccessTopic;

    @Value("${kafka.topic.group-buy-payment-failed:group-buy-payment-failed}")
    private String paymentFailedTopic;

    @Value("${kafka.topic.group-buy-delivery-complete:group-buy-delivery-complete}")
    private String deliveryCompleteTopic;

    /**
     * 결제 성공 이벤트 발행 (배송지 입력 요청)
     */
    public void publishPaymentSuccess(GroupBuy groupBuy, GroupBuyParticipant participant) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(
            groupBuy.getId(),
            participant.getId(),
            participant.getKakaoUuid(),
            participant.getNickname(),
            groupBuy.getProductName(),
            participant.getPaymentAmount(),
            groupBuy.getShareToken(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(paymentSuccessTopic, groupBuy.getId().toString(), event);
            log.info("Payment success event published: groupBuyId={}, participantId={}",
                groupBuy.getId(), participant.getId());
        } catch (Exception e) {
            log.error("Failed to publish payment success event: groupBuyId={}, participantId={}",
                groupBuy.getId(), participant.getId(), e);
        }
    }

    /**
     * 결제 실패 이벤트 발행 (재결제 안내)
     */
    public void publishPaymentFailed(GroupBuy groupBuy, GroupBuyParticipant participant, String errorMessage) {
        PaymentFailedEvent event = new PaymentFailedEvent(
            groupBuy.getId(),
            participant.getId(),
            participant.getKakaoUuid(),
            participant.getNickname(),
            groupBuy.getProductName(),
            groupBuy.getPricePerPerson(),
            errorMessage,
            groupBuy.getShareToken(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(paymentFailedTopic, groupBuy.getId().toString(), event);
            log.info("Payment failed event published: groupBuyId={}, participantId={}",
                groupBuy.getId(), participant.getId());
        } catch (Exception e) {
            log.error("Failed to publish payment failed event: groupBuyId={}, participantId={}",
                groupBuy.getId(), participant.getId(), e);
        }
    }

    /**
     * 배송 완료 이벤트 발행
     */
    public void publishDeliveryComplete(GroupBuy groupBuy, GroupBuyDelivery delivery) {
        DeliveryCompleteEvent event = new DeliveryCompleteEvent(
            groupBuy.getId(),
            delivery.getId(),
            groupBuy.getProductName(),
            delivery.getCarrierName(),
            delivery.getTrackingNumber(),
            delivery.getDeliveredAt(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(deliveryCompleteTopic, groupBuy.getId().toString(), event);
            log.info("Delivery complete event published: groupBuyId={}, deliveryId={}",
                groupBuy.getId(), delivery.getId());
        } catch (Exception e) {
            log.error("Failed to publish delivery complete event: groupBuyId={}, deliveryId={}",
                groupBuy.getId(), delivery.getId(), e);
        }
    }

    // Event DTOs
    public record PaymentSuccessEvent(
        Long groupBuyId,
        Long participantId,
        String kakaoUuid,
        String nickname,
        String productName,
        BigDecimal paymentAmount,
        String shareToken,
        LocalDateTime timestamp
    ) {}

    public record PaymentFailedEvent(
        Long groupBuyId,
        Long participantId,
        String kakaoUuid,
        String nickname,
        String productName,
        BigDecimal amount,
        String errorMessage,
        String shareToken,
        LocalDateTime timestamp
    ) {}

    public record DeliveryCompleteEvent(
        Long groupBuyId,
        Long deliveryId,
        String productName,
        String carrierName,
        String trackingNumber,
        LocalDateTime deliveredAt,
        LocalDateTime timestamp
    ) {}
}
