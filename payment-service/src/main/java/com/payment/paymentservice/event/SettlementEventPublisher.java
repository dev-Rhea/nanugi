package com.payment.paymentservice.event;

import com.payment.common.domain.PaymentStatus;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.repository.SettlementParticipantRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;

    @Value("${kafka.topic.settlement-notification}")
    private String notificationTopic;

    @Value("${kafka.topic.settlement-reminder}")
    private String reminderTopic;

    @Transactional
    public void publishNotification(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.IN_PROGRESS) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        List<SettlementNotificationEvent.ParticipantInfo> participantInfos = settlement.getParticipants().stream()
            .filter(p -> !p.isMessageSent())
            .map(p -> SettlementNotificationEvent.ParticipantInfo.builder()
                .uuid(p.getKakaoUuid())
                .nickname(p.getNickname())
                .amount(p.getTotalAmount())
                .build())
            .toList();

        if (participantInfos.isEmpty()) {
            log.info("No participants to notify for settlement {}", settlementId);
            return;
        }

        SettlementNotificationEvent event = SettlementNotificationEvent.builder()
            .settlementId(settlementId)
            .hostId(settlement.getHost().getId())
            .hostName(settlement.getHost().getNickname())
            .bank(settlement.getReceiverBank() != null ? settlement.getReceiverBank().name() : null)
            .accountNumber(settlement.getReceiverAccountNumber())
            .participants(participantInfos)
            .build();

        kafkaTemplate.send(notificationTopic, settlementId.toString(), event);

        settlement.getParticipants().stream()
            .filter(p -> !p.isMessageSent())
            .forEach(SettlementParticipant::markMessageSent);

        log.info("Published notification event for settlement {}: {} participants", 
            settlementId, participantInfos.size());
    }

    @Transactional
    public void publishReminder(Long settlementId, Long memberId) {
        Settlement settlement = settlementRepository.findByIdWithParticipants(settlementId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!settlement.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.IN_PROGRESS) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        List<SettlementReminderEvent.ParticipantInfo> unpaidParticipants = settlement.getParticipants().stream()
            .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
            .map(p -> SettlementReminderEvent.ParticipantInfo.builder()
                .uuid(p.getKakaoUuid())
                .nickname(p.getNickname())
                .amount(p.getTotalAmount())
                .build())
            .toList();

        if (unpaidParticipants.isEmpty()) {
            log.info("No unpaid participants to remind for settlement {}", settlementId);
            return;
        }

        SettlementReminderEvent event = SettlementReminderEvent.builder()
            .settlementId(settlementId)
            .hostName(settlement.getHost().getNickname())
            .participants(unpaidParticipants)
            .build();

        kafkaTemplate.send(reminderTopic, settlementId.toString(), event);

        log.info("Published reminder event for settlement {}: {} unpaid participants", 
            settlementId, unpaidParticipants.size());
    }
}
