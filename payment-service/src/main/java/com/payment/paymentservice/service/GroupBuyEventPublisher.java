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

    @Value("${kafka.topic.group-buy-shipping-started:group-buy-shipping-started}")
    private String shippingStartedTopic;

    @Value("${kafka.topic.group-buy-shipping-info-complete:group-buy-shipping-info-complete}")
    private String shippingInfoCompleteTopic;

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
     * 배송 시작 이벤트 발행 (운송장 등록 시)
     */
    public void publishShippingStarted(GroupBuy groupBuy) {
        GroupBuyDelivery delivery = groupBuy.getDelivery();
        ShippingStartedEvent event = new ShippingStartedEvent(
            groupBuy.getId(),
            groupBuy.getProductName(),
            delivery.getCarrierName(),
            delivery.getTrackingNumber(),
            groupBuy.getParticipants().stream()
                .map(GroupBuyParticipant::getKakaoUuid)
                .toList(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(shippingStartedTopic, groupBuy.getId().toString(), event);
            log.info("Shipping started event published: groupBuyId={}", groupBuy.getId());
        } catch (Exception e) {
            log.error("Failed to publish shipping started event: groupBuyId={}",
                groupBuy.getId(), e);
        }
    }

    /**
     * 배송 완료 이벤트 발행
     */
    public void publishDeliveryCompleted(GroupBuy groupBuy) {
        GroupBuyDelivery delivery = groupBuy.getDelivery();
        DeliveryCompleteEvent event = new DeliveryCompleteEvent(
            groupBuy.getId(),
            delivery != null ? delivery.getId() : null,
            groupBuy.getProductName(),
            delivery != null ? delivery.getCarrierName() : null,
            delivery != null ? delivery.getTrackingNumber() : null,
            delivery != null ? delivery.getDeliveredAt() : null,
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(deliveryCompleteTopic, groupBuy.getId().toString(), event);
            log.info("Delivery complete event published: groupBuyId={}", groupBuy.getId());
        } catch (Exception e) {
            log.error("Failed to publish delivery complete event: groupBuyId={}",
                groupBuy.getId(), e);
        }
    }

    /**
     * 모든 배송지 입력 완료 이벤트 발행 (방장에게 알림)
     */
    public void publishAllShippingInfoSubmitted(GroupBuy groupBuy) {
        ShippingInfoCompleteEvent event = new ShippingInfoCompleteEvent(
            groupBuy.getId(),
            groupBuy.getProductName(),
            groupBuy.getHost().getKakaoId(),
            groupBuy.getHost().getNickname(),
            groupBuy.getParticipants().size(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(shippingInfoCompleteTopic, groupBuy.getId().toString(), event);
            log.info("All shipping info submitted event published: groupBuyId={}",
                groupBuy.getId());
        } catch (Exception e) {
            log.error("Failed to publish shipping info complete event: groupBuyId={}",
                groupBuy.getId(), e);
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

    public record ShippingStartedEvent(
        Long groupBuyId,
        String productName,
        String carrierName,
        String trackingNumber,
        java.util.List<String> participantUuids,
        LocalDateTime timestamp
    ) {}

    public record ShippingInfoCompleteEvent(
        Long groupBuyId,
        String productName,
        String hostKakaoUuid,
        String hostNickname,
        int participantCount,
        LocalDateTime timestamp
    ) {}
}
