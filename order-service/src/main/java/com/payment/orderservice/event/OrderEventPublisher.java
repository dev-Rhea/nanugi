package com.payment.orderservice.event;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.group-buy-payment-request:group-buy.payment.request}")
    private String paymentRequestTopic;

    @Value("${kafka.topic.group-buy-shipping-started:group-buy.shipping.started}")
    private String shippingStartedTopic;

    @Value("${kafka.topic.group-buy-shipping-info-complete:group-buy.shipping-info.complete}")
    private String shippingInfoCompleteTopic;

    @Value("${kafka.topic.group-buy-delivery-complete:group-buy.delivery.complete}")
    private String deliveryCompleteTopic;

    /**
     * 결제 요청 이벤트 발행 (모집 확정 시)
     */
    public void publishPaymentRequest(GroupBuy groupBuy) {
        PaymentRequestEvent event = new PaymentRequestEvent(
            groupBuy.getId(),
            groupBuy.getProductName(),
            groupBuy.getPricePerPerson(),
            groupBuy.getParticipants().stream()
                .map(p -> new ParticipantInfo(p.getId(), p.getKakaoUuid(), p.getNickname(), p.getBillingKey()))
                .toList(),
            LocalDateTime.now()
        );

        try {
            kafkaTemplate.send(paymentRequestTopic, groupBuy.getId().toString(), event);
            log.info("Payment request event published: groupBuyId={}", groupBuy.getId());
        } catch (Exception e) {
            log.error("Failed to publish payment request event: groupBuyId={}", groupBuy.getId(), e);
        }
    }

    /**
     * 최소 인원 달성 알림
     */
    public void publishMinParticipantsReached(GroupBuy groupBuy) {
        log.info("Minimum participants reached for group buy: groupBuyId={}, current={}, min={}",
            groupBuy.getId(), groupBuy.getCurrentParticipantCount(), groupBuy.getMinParticipants());
    }

    /**
     * 배송 시작 이벤트 발행
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
            log.error("Failed to publish shipping started event: groupBuyId={}", groupBuy.getId(), e);
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
            log.error("Failed to publish delivery complete event: groupBuyId={}", groupBuy.getId(), e);
        }
    }

    /**
     * 모든 배송지 입력 완료 이벤트 발행
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
            log.info("All shipping info submitted event published: groupBuyId={}", groupBuy.getId());
        } catch (Exception e) {
            log.error("Failed to publish shipping info complete event: groupBuyId={}", groupBuy.getId(), e);
        }
    }

    // Event DTOs
    public record PaymentRequestEvent(
        Long groupBuyId,
        String productName,
        BigDecimal pricePerPerson,
        List<ParticipantInfo> participants,
        LocalDateTime timestamp
    ) {}

    public record ParticipantInfo(
        Long participantId,
        String kakaoUuid,
        String nickname,
        String billingKey
    ) {}

    public record ShippingStartedEvent(
        Long groupBuyId,
        String productName,
        String carrierName,
        String trackingNumber,
        List<String> participantUuids,
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

    public record ShippingInfoCompleteEvent(
        Long groupBuyId,
        String productName,
        String hostKakaoUuid,
        String hostNickname,
        int participantCount,
        LocalDateTime timestamp
    ) {}
}
