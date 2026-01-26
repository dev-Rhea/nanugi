package com.payment.orderservice.service;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyDelivery.DeliveryStatus;
import com.payment.common.domain.GroupBuyParticipant;
import com.payment.common.domain.GroupBuyParticipant.ParticipantPaymentStatus;
import com.payment.common.domain.GroupBuyStatus;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.orderservice.dto.request.RegisterTrackingRequest;
import com.payment.orderservice.dto.request.SubmitShippingInfoRequest;
import com.payment.orderservice.dto.response.DeliveryResponse;
import com.payment.orderservice.dto.response.DeliveryTrackingResponse;
import com.payment.orderservice.dto.response.GroupBuyParticipantResponse;
import com.payment.orderservice.dto.response.ShippingStatusResponse;
import com.payment.orderservice.dto.response.ShippingStatusResponse.ParticipantShippingInfo;
import com.payment.orderservice.event.OrderEventPublisher;
import com.payment.orderservice.repository.GroupBuyDeliveryRepository;
import com.payment.orderservice.repository.GroupBuyParticipantRepository;
import com.payment.orderservice.repository.GroupBuyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyParticipantRepository participantRepository;
    private final GroupBuyDeliveryRepository deliveryRepository;
    private final DeliveryTrackingService trackingService;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public GroupBuyParticipantResponse submitShippingInfo(String shareToken, String kakaoUuid,
                                                           SubmitShippingInfoRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (groupBuy.getStatus() != GroupBuyStatus.PAID) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuyParticipant participant = participantRepository
            .findByGroupBuyIdAndKakaoUuid(groupBuy.getId(), kakaoUuid)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (participant.getPaymentStatus() != ParticipantPaymentStatus.PAID) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        participant.submitShippingInfo(
            request.getAddress(),
            request.getPostalCode(),
            request.getReceiverName(),
            request.getReceiverPhone()
        );

        log.info("Shipping info submitted: groupBuyId={}, kakaoUuid={}", groupBuy.getId(), kakaoUuid);

        checkAllShippingInfoSubmitted(groupBuy);

        return GroupBuyParticipantResponse.from(participant);
    }

    @Transactional(readOnly = true)
    public ShippingStatusResponse getShippingStatus(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        List<GroupBuyParticipant> participants = groupBuy.getParticipants();
        int totalParticipants = participants.size();
        long submittedCount = participants.stream()
            .filter(GroupBuyParticipant::isShippingInfoSubmitted)
            .count();

        List<ParticipantShippingInfo> participantInfos = participants.stream()
            .map(p -> ParticipantShippingInfo.builder()
                .participantId(p.getId())
                .nickname(p.getNickname())
                .submitted(p.isShippingInfoSubmitted())
                .address(p.getShippingAddress())
                .postalCode(p.getShippingPostalCode())
                .receiverName(p.getShippingReceiverName())
                .receiverPhone(p.getShippingReceiverPhone())
                .build())
            .toList();

        return ShippingStatusResponse.builder()
            .groupBuyId(groupBuyId)
            .totalParticipants(totalParticipants)
            .submittedCount((int) submittedCount)
            .pendingCount((int) (totalParticipants - submittedCount))
            .allSubmitted(submittedCount == totalParticipants)
            .participants(participantInfos)
            .build();
    }

    @Transactional
    public DeliveryResponse registerTracking(Long groupBuyId, Long memberId, RegisterTrackingRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (groupBuy.getStatus() != GroupBuyStatus.PAID) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuyDelivery delivery = groupBuy.getDelivery();
        if (delivery == null) {
            delivery = GroupBuyDelivery.builder()
                .groupBuy(groupBuy)
                .build();
            deliveryRepository.save(delivery);
            groupBuy.setDelivery(delivery);
        }

        delivery.registerTrackingNumber(
            request.getCarrierCode(),
            request.getCarrierName(),
            request.getTrackingNumber()
        );

        groupBuy.updateStatus(GroupBuyStatus.SHIPPING);

        eventPublisher.publishShippingStarted(groupBuy);

        return DeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        boolean isHost = groupBuy.getHost().getId().equals(memberId);
        boolean isParticipant = groupBuy.getParticipants().stream()
            .anyMatch(p -> p.getMember() != null && p.getMember().getId().equals(memberId));

        if (!isHost && !isParticipant) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        GroupBuyDelivery delivery = groupBuy.getDelivery();
        if (delivery == null) {
            throw new CustomException(CustomErrorCode.NOT_FOUND);
        }

        return DeliveryResponse.from(delivery);
    }

    @Transactional
    public DeliveryTrackingResponse trackDelivery(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        boolean isHost = groupBuy.getHost().getId().equals(memberId);
        boolean isParticipant = groupBuy.getParticipants().stream()
            .anyMatch(p -> p.getMember() != null && p.getMember().getId().equals(memberId));

        if (!isHost && !isParticipant) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        GroupBuyDelivery delivery = groupBuy.getDelivery();
        if (delivery == null || delivery.getTrackingNumber() == null) {
            throw new CustomException(CustomErrorCode.NOT_FOUND);
        }

        DeliveryTrackingResponse trackingResponse = trackingService.track(
            delivery.getCarrierCode(),
            delivery.getTrackingNumber()
        );

        updateDeliveryStatus(delivery, trackingResponse, groupBuy);

        return trackingResponse;
    }

    @Transactional
    public DeliveryResponse completeDelivery(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (groupBuy.getStatus() != GroupBuyStatus.SHIPPING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuyDelivery delivery = groupBuy.getDelivery();
        if (delivery == null) {
            throw new CustomException(CustomErrorCode.NOT_FOUND);
        }

        delivery.updateTrackingStatus(DeliveryStatus.DELIVERED, "배송 완료 (수동 확인)");
        groupBuy.updateStatus(GroupBuyStatus.COMPLETED);

        eventPublisher.publishDeliveryCompleted(groupBuy);

        return DeliveryResponse.from(delivery);
    }

    private void checkAllShippingInfoSubmitted(GroupBuy groupBuy) {
        boolean allSubmitted = groupBuy.getParticipants().stream()
            .filter(p -> p.getPaymentStatus() == ParticipantPaymentStatus.PAID)
            .allMatch(GroupBuyParticipant::isShippingInfoSubmitted);

        if (allSubmitted) {
            eventPublisher.publishAllShippingInfoSubmitted(groupBuy);
        }
    }

    private void updateDeliveryStatus(GroupBuyDelivery delivery, DeliveryTrackingResponse tracking, GroupBuy groupBuy) {
        DeliveryStatus newStatus = mapTrackingStatus(tracking.getCurrentStatus());
        delivery.updateTrackingStatus(newStatus, tracking.getCurrentStatusDetail());

        if (newStatus == DeliveryStatus.DELIVERED && groupBuy.getStatus() == GroupBuyStatus.SHIPPING) {
            groupBuy.updateStatus(GroupBuyStatus.COMPLETED);
            eventPublisher.publishDeliveryCompleted(groupBuy);
        }
    }

    private DeliveryStatus mapTrackingStatus(String status) {
        if (status == null) return DeliveryStatus.IN_TRANSIT;
        return switch (status.toUpperCase()) {
            case "DELIVERED", "COMPLETE", "완료" -> DeliveryStatus.DELIVERED;
            case "OUT_FOR_DELIVERY", "배송출발" -> DeliveryStatus.OUT_FOR_DELIVERY;
            case "FAILED", "실패" -> DeliveryStatus.FAILED;
            default -> DeliveryStatus.IN_TRANSIT;
        };
    }
}
