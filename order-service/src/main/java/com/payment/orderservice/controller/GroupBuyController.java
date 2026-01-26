package com.payment.orderservice.controller;

import com.payment.orderservice.dto.request.CreateGroupBuyRequest;
import com.payment.orderservice.dto.request.JoinGroupBuyRequest;
import com.payment.orderservice.dto.request.RegisterBillingKeyRequest;
import com.payment.orderservice.dto.request.RegisterTrackingRequest;
import com.payment.orderservice.dto.request.SubmitShippingInfoRequest;
import com.payment.orderservice.dto.response.DeliveryResponse;
import com.payment.orderservice.dto.response.DeliveryTrackingResponse;
import com.payment.orderservice.dto.response.GroupBuyListResponse;
import com.payment.orderservice.dto.response.GroupBuyParticipantResponse;
import com.payment.orderservice.dto.response.GroupBuyResponse;
import com.payment.orderservice.dto.response.KakaoShareTemplateResponse;
import com.payment.orderservice.dto.response.ShippingStatusResponse;
import com.payment.orderservice.service.DeliveryService;
import com.payment.orderservice.service.GroupBuyService;
import com.payment.orderservice.service.GroupBuyService.BillingKeyStatusResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-buys")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final DeliveryService deliveryService;

    // ==================== Phase 1: 공동구매 생성/관리 ====================

    @PostMapping
    public ResponseEntity<GroupBuyResponse> createGroupBuy(
        @AuthenticationPrincipal Long memberId,
        @RequestBody CreateGroupBuyRequest request) {
        GroupBuyResponse response = groupBuyService.createGroupBuy(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupBuyListResponse>> getMyGroupBuys(
        @AuthenticationPrincipal Long memberId) {
        List<GroupBuyListResponse> response = groupBuyService.getMyGroupBuys(memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupBuyResponse> getGroupBuy(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.getGroupBuy(id, memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public/{shareToken}")
    public ResponseEntity<GroupBuyResponse> getGroupBuyByShareToken(
        @PathVariable String shareToken) {
        GroupBuyResponse response = groupBuyService.getGroupBuyByShareToken(shareToken);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/product")
    public ResponseEntity<GroupBuyResponse> updateProductInfo(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId,
        @RequestParam String productName,
        @RequestParam BigDecimal productPrice,
        @RequestParam(required = false) String productImageUrl,
        @RequestParam(required = false) String productDescription) {
        GroupBuyResponse response = groupBuyService.updateProductInfo(
            id, memberId, productName, productPrice, productImageUrl, productDescription);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/recruitment")
    public ResponseEntity<GroupBuyResponse> updateRecruitmentSettings(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId,
        @RequestParam int minParticipants,
        @RequestParam int maxParticipants,
        @RequestParam LocalDateTime recruitmentDeadline) {
        GroupBuyResponse response = groupBuyService.updateRecruitmentSettings(
            id, memberId, minParticipants, maxParticipants, recruitmentDeadline);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<GroupBuyResponse> cancelGroupBuy(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.cancelGroupBuy(id, memberId);
        return ResponseEntity.ok(response);
    }

    // ==================== Phase 2: 참여 및 결제 예약 ====================

    @GetMapping("/{id}/share-template")
    public ResponseEntity<KakaoShareTemplateResponse> getKakaoShareTemplate(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        KakaoShareTemplateResponse response = groupBuyService.getKakaoShareTemplate(id, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/public/{shareToken}/join")
    public ResponseEntity<GroupBuyParticipantResponse> joinGroupBuy(
        @PathVariable String shareToken,
        @AuthenticationPrincipal Long memberId,
        @RequestBody JoinGroupBuyRequest request) {
        GroupBuyParticipantResponse response = groupBuyService.joinGroupBuy(shareToken, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/public/{shareToken}/leave")
    public ResponseEntity<Void> leaveGroupBuy(
        @PathVariable String shareToken,
        @RequestParam String kakaoUuid) {
        groupBuyService.leaveGroupBuy(shareToken, kakaoUuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/public/{shareToken}/billing-key")
    public ResponseEntity<GroupBuyParticipantResponse> registerBillingKey(
        @PathVariable String shareToken,
        @RequestParam String kakaoUuid,
        @RequestBody RegisterBillingKeyRequest request) {
        GroupBuyParticipantResponse response = groupBuyService.registerBillingKey(shareToken, kakaoUuid, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<GroupBuyParticipantResponse>> getParticipants(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        List<GroupBuyParticipantResponse> response = groupBuyService.getParticipants(id, memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/billing-key-status")
    public ResponseEntity<BillingKeyStatusResponse> getBillingKeyStatus(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        BillingKeyStatusResponse response = groupBuyService.getBillingKeyStatus(id, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<GroupBuyResponse> confirmRecruitment(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.confirmRecruitment(id, memberId);
        return ResponseEntity.ok(response);
    }

    // ==================== Phase 3: 배송 관리 ====================

    @PostMapping("/public/{shareToken}/shipping-info")
    public ResponseEntity<GroupBuyParticipantResponse> submitShippingInfo(
        @PathVariable String shareToken,
        @RequestParam String kakaoUuid,
        @RequestBody SubmitShippingInfoRequest request) {
        GroupBuyParticipantResponse response = deliveryService.submitShippingInfo(shareToken, kakaoUuid, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/shipping-status")
    public ResponseEntity<ShippingStatusResponse> getShippingStatus(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        ShippingStatusResponse response = deliveryService.getShippingStatus(id, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/tracking")
    public ResponseEntity<DeliveryResponse> registerTracking(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId,
        @RequestBody RegisterTrackingRequest request) {
        DeliveryResponse response = deliveryService.registerTracking(id, memberId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/delivery")
    public ResponseEntity<DeliveryResponse> getDelivery(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        DeliveryResponse response = deliveryService.getDelivery(id, memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/delivery/tracking")
    public ResponseEntity<DeliveryTrackingResponse> trackDelivery(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        DeliveryTrackingResponse response = deliveryService.trackDelivery(id, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/delivery/complete")
    public ResponseEntity<DeliveryResponse> completeDelivery(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        DeliveryResponse response = deliveryService.completeDelivery(id, memberId);
        return ResponseEntity.ok(response);
    }
}
