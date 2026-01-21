package com.payment.paymentservice.service;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyParticipant;
import com.payment.common.domain.GroupBuyParticipant.ParticipantPaymentStatus;
import com.payment.common.domain.GroupBuyStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.payment.PaymentGateway;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import com.payment.paymentservice.repository.GroupBuyDeliveryRepository;
import com.payment.paymentservice.repository.GroupBuyParticipantRepository;
import com.payment.paymentservice.repository.GroupBuyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyPaymentService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyParticipantRepository participantRepository;
    private final GroupBuyDeliveryRepository deliveryRepository;
    private final PaymentGateway paymentGateway;
    private final GroupBuyEventPublisher eventPublisher;

    private final ExecutorService paymentExecutor = Executors.newFixedThreadPool(10);

    /**
     * 일괄 결제 실행 (모집 확정 후)
     */
    @Transactional
    public BatchPaymentResult processBatchPayment(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        // CONFIRMED 상태에서만 결제 가능
        if (groupBuy.getStatus() != GroupBuyStatus.CONFIRMED) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.updateStatus(GroupBuyStatus.PAYMENT_PROCESSING);

        BigDecimal pricePerPerson = groupBuy.getPricePerPerson();
        String orderNamePrefix = String.format("[공동구매] %s", groupBuy.getProductName());

        List<GroupBuyParticipant> participants = groupBuy.getParticipants();
        List<PaymentResultItem> results = new ArrayList<>();
        List<CompletableFuture<PaymentResultItem>> futures = new ArrayList<>();

        // 비동기 병렬 결제 처리
        for (GroupBuyParticipant participant : participants) {
            if (participant.getPaymentStatus() != ParticipantPaymentStatus.PAYMENT_RESERVED) {
                continue;
            }

            CompletableFuture<PaymentResultItem> future = CompletableFuture.supplyAsync(() -> {
                String orderId = generateOrderId(groupBuyId, participant.getId());
                String orderName = orderNamePrefix + " - " + participant.getNickname();

                PaymentResult result = paymentGateway.processPayment(
                    participant.getBillingKey(),
                    orderId,
                    pricePerPerson,
                    orderName
                );

                return new PaymentResultItem(participant.getId(), participant.getNickname(), result);
            }, paymentExecutor);

            futures.add(future);
        }

        // 모든 결제 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 결과 수집 및 처리
        int successCount = 0;
        int failCount = 0;
        List<String> failedParticipants = new ArrayList<>();

        for (CompletableFuture<PaymentResultItem> future : futures) {
            PaymentResultItem item = future.join();
            results.add(item);

            GroupBuyParticipant participant = participantRepository.findById(item.participantId())
                .orElse(null);

            if (participant == null) continue;

            if (item.result().success()) {
                participant.markPaymentSuccess(
                    pricePerPerson,
                    item.result().paymentKey(),
                    item.result().receiptUrl()
                );
                successCount++;

                // 결제 성공 이벤트 발행 (배송지 입력 알림)
                eventPublisher.publishPaymentSuccess(groupBuy, participant);
            } else {
                participant.markPaymentFailed();
                failCount++;
                failedParticipants.add(participant.getNickname());

                // 결제 실패 이벤트 발행 (재결제 안내)
                eventPublisher.publishPaymentFailed(groupBuy, participant, item.result().errorMessage());
            }
        }

        // 전체 결제 완료 여부 확인
        boolean allSuccess = failCount == 0;
        if (allSuccess) {
            groupBuy.updateStatus(GroupBuyStatus.PAID);

            // 배송 정보 생성
            GroupBuyDelivery delivery = GroupBuyDelivery.builder()
                .groupBuy(groupBuy)
                .build();
            deliveryRepository.save(delivery);
            groupBuy.setDelivery(delivery);

            log.info("All payments successful for group buy: groupBuyId={}, count={}",
                groupBuyId, successCount);
        } else {
            log.warn("Some payments failed for group buy: groupBuyId={}, success={}, fail={}",
                groupBuyId, successCount, failCount);
        }

        return new BatchPaymentResult(
            groupBuyId,
            successCount,
            failCount,
            failedParticipants,
            allSuccess,
            groupBuy.getStatus()
        );
    }

    /**
     * 결제 실패자 재결제 (수동)
     */
    @Transactional
    public PaymentResult retryPayment(Long groupBuyId, Long participantId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        GroupBuyParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!participant.getGroupBuy().getId().equals(groupBuyId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (participant.getPaymentStatus() != ParticipantPaymentStatus.PAYMENT_FAILED) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        BigDecimal pricePerPerson = groupBuy.getPricePerPerson();
        String orderId = generateOrderId(groupBuyId, participantId);
        String orderName = String.format("[공동구매] %s - %s", groupBuy.getProductName(), participant.getNickname());

        PaymentResult result = paymentGateway.processPayment(
            participant.getBillingKey(),
            orderId,
            pricePerPerson,
            orderName
        );

        if (result.success()) {
            participant.markPaymentSuccess(pricePerPerson, result.paymentKey(), result.receiptUrl());

            // 모든 참여자 결제 완료 확인
            checkAllPaymentsComplete(groupBuy);

            eventPublisher.publishPaymentSuccess(groupBuy, participant);
        } else {
            participant.markPaymentFailed();
            eventPublisher.publishPaymentFailed(groupBuy, participant, result.errorMessage());
        }

        return result;
    }

    /**
     * 환불 처리
     */
    @Transactional
    public PaymentResult refundPayment(Long groupBuyId, Long participantId, String reason) {
        GroupBuyParticipant participant = participantRepository.findById(participantId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!participant.getGroupBuy().getId().equals(groupBuyId)) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (participant.getPaymentStatus() != ParticipantPaymentStatus.PAID) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        PaymentResult result = paymentGateway.cancelPayment(participant.getPaymentKey(), reason);

        if (result.success()) {
            participant.markRefunded();
            log.info("Payment refunded: groupBuyId={}, participantId={}", groupBuyId, participantId);
        }

        return result;
    }

    private void checkAllPaymentsComplete(GroupBuy groupBuy) {
        boolean allPaid = groupBuy.getParticipants().stream()
            .allMatch(p -> p.getPaymentStatus() == ParticipantPaymentStatus.PAID);

        if (allPaid && groupBuy.getStatus() == GroupBuyStatus.PAYMENT_PROCESSING) {
            groupBuy.updateStatus(GroupBuyStatus.PAID);

            if (groupBuy.getDelivery() == null) {
                GroupBuyDelivery delivery = GroupBuyDelivery.builder()
                    .groupBuy(groupBuy)
                    .build();
                deliveryRepository.save(delivery);
                groupBuy.setDelivery(delivery);
            }

            log.info("All payments completed for group buy: groupBuyId={}", groupBuy.getId());
        }
    }

    private String generateOrderId(Long groupBuyId, Long participantId) {
        return String.format("GB%d_P%d_%s", groupBuyId, participantId,
            UUID.randomUUID().toString().substring(0, 8));
    }

    public record BatchPaymentResult(
        Long groupBuyId,
        int successCount,
        int failCount,
        List<String> failedParticipants,
        boolean allSuccess,
        GroupBuyStatus status
    ) {}

    public record PaymentResultItem(
        Long participantId,
        String nickname,
        PaymentResult result
    ) {}
}
