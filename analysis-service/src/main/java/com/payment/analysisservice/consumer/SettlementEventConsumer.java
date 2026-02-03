package com.payment.analysisservice.consumer;

import com.payment.analysisservice.dto.event.SettlementCancelledEvent;
import com.payment.analysisservice.dto.event.SettlementCompletedEvent;
import com.payment.analysisservice.repository.SpendingRecordRepository;
import com.payment.analysisservice.service.SpendingAggregationService;
import com.payment.common.domain.SpendingRecord;
import com.payment.common.domain.SpendingStatus;
import com.payment.common.domain.SpendingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventConsumer {

    private final SpendingRecordRepository spendingRecordRepository;
    private final SpendingAggregationService aggregationService;

    @Transactional
    @KafkaListener(topics = "settlement-completed", groupId = "analysis-service-group")
    public void handleSettlementCompleted(SettlementCompletedEvent event) {
        log.info("Received SettlementCompletedEvent: settlementId={}", event.getSettlementId());

        spendingRecordRepository.findByTypeAndSourceId(SpendingType.SETTLEMENT, event.getSettlementId())
            .ifPresentOrElse(
                existing -> log.warn("SpendingRecord already exists for settlementId={}",
                    event.getSettlementId()),
                () -> {
                    SpendingRecord record = SpendingRecord.builder()
                        .memberId(event.getHostId())
                        .type(SpendingType.SETTLEMENT)
                        .sourceId(event.getSettlementId())
                        .title(event.getTitle())
                        .totalAmount(event.getTotalAmount())
                        .amountPerPerson(event.getAmountPerPerson())
                        .category(event.getStoreName())
                        .participantCount(event.getParticipantCount())
                        .status(SpendingStatus.COMPLETED)
                        .completedAt(event.getCompletedAt())
                        .build();
                    spendingRecordRepository.save(record);
                    log.info("Created SpendingRecord for settlementId={}", event.getSettlementId());

                    aggregationService.refreshSummary(event.getHostId(),
                        event.getCompletedAt().toLocalDate());
                }
            );
    }

    @Transactional
    @KafkaListener(topics = "settlement-cancelled", groupId = "analysis-service-group")
    public void handleSettlementCancelled(SettlementCancelledEvent event) {
        log.info("Received SettlementCancelledEvent: settlementId={}", event.getSettlementId());

        spendingRecordRepository.findByTypeAndSourceId(SpendingType.SETTLEMENT, event.getSettlementId())
            .ifPresent(record -> {
                record.updateStatus(SpendingStatus.CANCELLED);
                log.info("Updated SpendingRecord status to CANCELLED for settlementId={}",
                    event.getSettlementId());
            });
    }
}
