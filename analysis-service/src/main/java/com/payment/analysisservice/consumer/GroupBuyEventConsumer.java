package com.payment.analysisservice.consumer;

import com.payment.analysisservice.dto.event.GroupBuyCancelledEvent;
import com.payment.analysisservice.dto.event.GroupBuyCompletedEvent;
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
public class GroupBuyEventConsumer {

    private final SpendingRecordRepository spendingRecordRepository;
    private final SpendingAggregationService aggregationService;

    @Transactional
    @KafkaListener(topics = "group-buy-completed", groupId = "analysis-service-group")
    public void handleGroupBuyCompleted(GroupBuyCompletedEvent event) {
        log.info("Received GroupBuyCompletedEvent: groupBuyId={}", event.getGroupBuyId());

        spendingRecordRepository.findByTypeAndSourceId(SpendingType.GROUP_BUY, event.getGroupBuyId())
            .ifPresentOrElse(
                existing -> log.warn("SpendingRecord already exists for groupBuyId={}",
                    event.getGroupBuyId()),
                () -> {
                    SpendingRecord record = SpendingRecord.builder()
                        .memberId(event.getHostId())
                        .type(SpendingType.GROUP_BUY)
                        .sourceId(event.getGroupBuyId())
                        .title(event.getProductName())
                        .totalAmount(event.getProductPrice())
                        .amountPerPerson(event.getPricePerPerson())
                        .category(event.getDomain())
                        .participantCount(event.getParticipantCount())
                        .status(SpendingStatus.COMPLETED)
                        .completedAt(event.getCompletedAt())
                        .build();
                    spendingRecordRepository.save(record);
                    log.info("Created SpendingRecord for groupBuyId={}", event.getGroupBuyId());

                    aggregationService.refreshSummary(event.getHostId(),
                        event.getCompletedAt().toLocalDate());
                }
            );
    }

    @Transactional
    @KafkaListener(topics = "group-buy-cancelled", groupId = "analysis-service-group")
    public void handleGroupBuyCancelled(GroupBuyCancelledEvent event) {
        log.info("Received GroupBuyCancelledEvent: groupBuyId={}", event.getGroupBuyId());

        spendingRecordRepository.findByTypeAndSourceId(SpendingType.GROUP_BUY, event.getGroupBuyId())
            .ifPresent(record -> {
                record.updateStatus(SpendingStatus.CANCELLED);
                log.info("Updated SpendingRecord status to CANCELLED for groupBuyId={}",
                    event.getGroupBuyId());
            });
    }
}
