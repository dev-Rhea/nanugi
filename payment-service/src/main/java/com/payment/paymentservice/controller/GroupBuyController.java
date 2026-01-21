package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.request.CrawlProductRequest;
import com.payment.paymentservice.dto.request.CreateGroupBuyRequest;
import com.payment.paymentservice.dto.request.JoinGroupBuyRequest;
import com.payment.paymentservice.dto.request.RegisterBillingKeyRequest;
import com.payment.paymentservice.dto.response.CrawlProductResponse;
import com.payment.paymentservice.dto.response.GroupBuyListResponse;
import com.payment.paymentservice.dto.response.GroupBuyParticipantResponse;
import com.payment.paymentservice.dto.response.GroupBuyResponse;
import com.payment.paymentservice.dto.response.KakaoShareTemplateResponse;
import com.payment.paymentservice.service.GroupBuyService;
import com.payment.paymentservice.service.GroupBuyService.BillingKeyStatusResponse;
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

    /**
     * URL에서 상품 정보 크롤링 (미리보기)
     */
    @PostMapping("/crawl")
    public ResponseEntity<CrawlProductResponse> crawlProduct(
        @RequestBody CrawlProductRequest request) {
        CrawlProductResponse response = groupBuyService.crawlProduct(request.getUrl());
        return ResponseEntity.ok(response);
    }

    /**
     * 공동구매 생성
     */
    @PostMapping
    public ResponseEntity<GroupBuyResponse> createGroupBuy(
        @AuthenticationPrincipal Long memberId,
        @RequestBody CreateGroupBuyRequest request) {
        GroupBuyResponse response = groupBuyService.createGroupBuy(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 내 공동구매 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<GroupBuyListResponse>> getMyGroupBuys(
        @AuthenticationPrincipal Long memberId) {
        List<GroupBuyListResponse> response = groupBuyService.getMyGroupBuys(memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 공동구매 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<GroupBuyResponse> getGroupBuy(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.getGroupBuy(id, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 공유 토큰으로 공동구매 조회 (공개)
     */
    @GetMapping("/public/{shareToken}")
    public ResponseEntity<GroupBuyResponse> getGroupBuyByShareToken(
        @PathVariable String shareToken) {
        GroupBuyResponse response = groupBuyService.getGroupBuyByShareToken(shareToken);
        return ResponseEntity.ok(response);
    }

    /**
     * 공동구매 상품 정보 수정
     */
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

    /**
     * 공동구매 모집 설정 수정
     */
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

    /**
     * 공동구매 취소
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<GroupBuyResponse> cancelGroupBuy(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.cancelGroupBuy(id, memberId);
        return ResponseEntity.ok(response);
    }

    // ==================== Phase 2: 참여 및 결제 예약 ====================

    /**
     * 카카오톡 공유 템플릿 데이터 조회
     */
    @GetMapping("/{id}/share-template")
    public ResponseEntity<KakaoShareTemplateResponse> getKakaoShareTemplate(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        KakaoShareTemplateResponse response = groupBuyService.getKakaoShareTemplate(id, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 공동구매 참여 (공유 토큰으로)
     */
    @PostMapping("/public/{shareToken}/join")
    public ResponseEntity<GroupBuyParticipantResponse> joinGroupBuy(
        @PathVariable String shareToken,
        @AuthenticationPrincipal Long memberId,
        @RequestBody JoinGroupBuyRequest request) {
        GroupBuyParticipantResponse response = groupBuyService.joinGroupBuy(shareToken, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 공동구매 참여 취소
     */
    @DeleteMapping("/public/{shareToken}/leave")
    public ResponseEntity<Void> leaveGroupBuy(
        @PathVariable String shareToken,
        @RequestParam String kakaoUuid) {
        groupBuyService.leaveGroupBuy(shareToken, kakaoUuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * 빌링키 등록 (결제 예약)
     */
    @PostMapping("/public/{shareToken}/billing-key")
    public ResponseEntity<GroupBuyParticipantResponse> registerBillingKey(
        @PathVariable String shareToken,
        @RequestParam String kakaoUuid,
        @RequestBody RegisterBillingKeyRequest request) {
        GroupBuyParticipantResponse response = groupBuyService.registerBillingKey(shareToken, kakaoUuid, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 참여자 목록 조회
     */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<GroupBuyParticipantResponse>> getParticipants(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        List<GroupBuyParticipantResponse> response = groupBuyService.getParticipants(id, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 빌링키 등록 현황 조회
     */
    @GetMapping("/{id}/billing-key-status")
    public ResponseEntity<BillingKeyStatusResponse> getBillingKeyStatus(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        BillingKeyStatusResponse response = groupBuyService.getBillingKeyStatus(id, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 모집 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<GroupBuyResponse> confirmRecruitment(
        @PathVariable Long id,
        @AuthenticationPrincipal Long memberId) {
        GroupBuyResponse response = groupBuyService.confirmRecruitment(id, memberId);
        return ResponseEntity.ok(response);
    }
}
