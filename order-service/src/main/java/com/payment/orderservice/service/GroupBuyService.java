package com.payment.orderservice.service;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyParticipant;
import com.payment.common.domain.GroupBuyStatus;
import com.payment.common.domain.Member;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import com.payment.orderservice.client.ProductServiceClient;
import com.payment.orderservice.dto.request.CrawlProductRequest;
import com.payment.orderservice.dto.request.CreateGroupBuyRequest;
import com.payment.orderservice.dto.request.JoinGroupBuyRequest;
import com.payment.orderservice.dto.request.RegisterBillingKeyRequest;
import com.payment.orderservice.dto.response.CrawlProductResponse;
import com.payment.orderservice.dto.response.GroupBuyListResponse;
import com.payment.orderservice.dto.response.GroupBuyParticipantResponse;
import com.payment.orderservice.dto.response.GroupBuyResponse;
import com.payment.orderservice.dto.response.KakaoShareTemplateResponse;
import com.payment.orderservice.repository.GroupBuyParticipantRepository;
import com.payment.orderservice.repository.GroupBuyRepository;
import com.payment.orderservice.repository.MemberRepository;
import com.payment.orderservice.event.OrderEventPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyParticipantRepository participantRepository;
    private final MemberRepository memberRepository;
    private final ProductServiceClient productServiceClient;
    private final OrderEventPublisher eventPublisher;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    /**
     * URL에서 상품 정보 크롤링 (미리보기)
     */
    public CrawlProductResponse crawlProduct(String url) {
        CrawlProductRequest request = new CrawlProductRequest();
        return productServiceClient.crawlProduct(request);
    }

    /**
     * 공동구매 생성
     */
    @Transactional
    public GroupBuyResponse createGroupBuy(Long memberId, CreateGroupBuyRequest request) {
        Member host = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        String productName = request.getProductName();
        java.math.BigDecimal productPrice = request.getProductPrice();
        String productImageUrl = request.getProductImageUrl();
        String productDescription = request.getProductDescription();
        boolean crawledProduct = false;

        // URL이 있으면 크롤링 시도
        if (request.getProductUrl() != null && !request.getProductUrl().isBlank()) {
            try {
                CrawlProductRequest crawlRequest = new CrawlProductRequest();
                CrawlProductResponse crawlResult = productServiceClient.crawlProduct(crawlRequest);

                if (crawlResult.isSuccess()) {
                    productName = request.getProductName() != null ? request.getProductName() : crawlResult.getProductName();
                    productPrice = request.getProductPrice() != null ? request.getProductPrice() : crawlResult.getProductPrice();
                    productImageUrl = request.getProductImageUrl() != null ? request.getProductImageUrl() : crawlResult.getProductImageUrl();
                    productDescription = request.getProductDescription() != null ? request.getProductDescription() : crawlResult.getProductDescription();
                    crawledProduct = true;
                    log.info("Product crawled successfully from URL: {}", request.getProductUrl());
                }
            } catch (Exception e) {
                log.warn("Crawling failed for URL: {}, error: {}", request.getProductUrl(), e.getMessage());
            }
        }

        // 필수 정보 검증
        if (productName == null || productName.isBlank()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }
        if (productPrice == null || productPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        // 모집 설정 검증
        if (request.getMinParticipants() < 1) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }
        if (request.getMaxParticipants() < request.getMinParticipants()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }
        if (request.getRecruitmentDeadline() == null ||
            request.getRecruitmentDeadline().isBefore(java.time.LocalDateTime.now())) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuy groupBuy = GroupBuy.builder()
            .productName(productName)
            .productPrice(productPrice)
            .productImageUrl(productImageUrl)
            .productUrl(request.getProductUrl())
            .productDescription(productDescription)
            .minParticipants(request.getMinParticipants())
            .maxParticipants(request.getMaxParticipants())
            .recruitmentDeadline(request.getRecruitmentDeadline())
            .host(host)
            .crawledProduct(crawledProduct)
            .build();

        groupBuyRepository.save(groupBuy);

        log.info("GroupBuy created: id={}, productName={}, hostId={}",
            groupBuy.getId(), groupBuy.getProductName(), memberId);

        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 내 공동구매 목록 조회
     */
    @Transactional(readOnly = true)
    public List<GroupBuyListResponse> getMyGroupBuys(Long memberId) {
        List<GroupBuy> groupBuys = groupBuyRepository.findByHost_IdOrderByCreatedAtDesc(memberId);
        return groupBuys.stream()
            .map(GroupBuyListResponse::from)
            .toList();
    }

    /**
     * 공동구매 상세 조회
     */
    @Transactional(readOnly = true)
    public GroupBuyResponse getGroupBuy(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        boolean isHost = groupBuy.getHost().getId().equals(memberId);
        boolean isParticipant = groupBuy.getParticipants().stream()
            .anyMatch(p -> p.getMember() != null && p.getMember().getId().equals(memberId));

        if (!isHost && !isParticipant) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 공유 토큰으로 공동구매 조회 (공개)
     */
    @Transactional(readOnly = true)
    public GroupBuyResponse getGroupBuyByShareToken(String shareToken) {
        GroupBuy groupBuy = groupBuyRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));
        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 공동구매 상품 정보 수정
     */
    @Transactional
    public GroupBuyResponse updateProductInfo(Long groupBuyId, Long memberId,
                                               String productName, java.math.BigDecimal productPrice,
                                               String productImageUrl, String productDescription) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.updateProductInfo(productName, productPrice, productImageUrl, productDescription);

        return GroupBuyResponse.fromWithoutParticipants(groupBuy);
    }

    /**
     * 공동구매 모집 설정 수정
     */
    @Transactional
    public GroupBuyResponse updateRecruitmentSettings(Long groupBuyId, Long memberId,
                                                       int minParticipants, int maxParticipants,
                                                       java.time.LocalDateTime recruitmentDeadline) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (maxParticipants < groupBuy.getCurrentParticipantCount()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.updateRecruitmentSettings(minParticipants, maxParticipants, recruitmentDeadline);

        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 공동구매 취소
     */
    @Transactional
    public GroupBuyResponse cancelGroupBuy(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        GroupBuyStatus currentStatus = groupBuy.getStatus();
        if (currentStatus != GroupBuyStatus.RECRUITING && currentStatus != GroupBuyStatus.CONFIRMED) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.updateStatus(GroupBuyStatus.CANCELLED);

        return GroupBuyResponse.fromWithoutParticipants(groupBuy);
    }

    // ==================== Phase 2: 참여 및 결제 예약 ====================

    /**
     * 카카오톡 공유 템플릿 데이터 조회
     */
    @Transactional(readOnly = true)
    public KakaoShareTemplateResponse getKakaoShareTemplate(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        return KakaoShareTemplateResponse.from(groupBuy, baseUrl);
    }

    /**
     * 공동구매 참여
     */
    @Transactional
    public GroupBuyParticipantResponse joinGroupBuy(String shareToken, Long memberId, JoinGroupBuyRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.isRecruitmentOpen()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        boolean alreadyJoined = participantRepository.existsByGroupBuyIdAndKakaoUuid(
            groupBuy.getId(), request.getKakaoUuid());
        if (alreadyJoined) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (groupBuy.isMaxParticipantsReached()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        Member member = null;
        if (memberId != null) {
            member = memberRepository.findById(memberId).orElse(null);
        }

        GroupBuyParticipant participant = GroupBuyParticipant.builder()
            .groupBuy(groupBuy)
            .member(member)
            .kakaoUuid(request.getKakaoUuid())
            .nickname(request.getNickname())
            .build();

        participantRepository.save(participant);
        groupBuy.addParticipant(participant);

        log.info("Participant joined group buy: groupBuyId={}, kakaoUuid={}",
            groupBuy.getId(), request.getKakaoUuid());

        checkMinParticipantsReached(groupBuy);

        return GroupBuyParticipantResponse.from(participant);
    }

    /**
     * 공동구매 참여 취소
     */
    @Transactional
    public void leaveGroupBuy(String shareToken, String kakaoUuid) {
        GroupBuy groupBuy = groupBuyRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuyParticipant participant = participantRepository
            .findByGroupBuyIdAndKakaoUuid(groupBuy.getId(), kakaoUuid)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (participant.isBillingKeyRegistered()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.removeParticipant(participant);
        participantRepository.delete(participant);
    }

    /**
     * 빌링키 등록
     */
    @Transactional
    public GroupBuyParticipantResponse registerBillingKey(String shareToken, String kakaoUuid,
                                                           RegisterBillingKeyRequest request) {
        GroupBuy groupBuy = groupBuyRepository.findByShareTokenWithParticipants(shareToken)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        GroupBuyStatus status = groupBuy.getStatus();
        if (status != GroupBuyStatus.RECRUITING && status != GroupBuyStatus.CONFIRMED) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        GroupBuyParticipant participant = participantRepository
            .findByGroupBuyIdAndKakaoUuid(groupBuy.getId(), kakaoUuid)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (participant.isBillingKeyRegistered()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        participant.registerBillingKey(
            request.getBillingKey(),
            request.getCardCompany(),
            request.getCardNumberMasked()
        );

        return GroupBuyParticipantResponse.from(participant);
    }

    /**
     * 참여자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<GroupBuyParticipantResponse> getParticipants(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        return groupBuy.getParticipants().stream()
            .map(GroupBuyParticipantResponse::from)
            .toList();
    }

    /**
     * 모집 확정
     */
    @Transactional
    public GroupBuyResponse confirmRecruitment(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        if (groupBuy.getStatus() != GroupBuyStatus.RECRUITING) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        if (!groupBuy.isMinParticipantsReached()) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        boolean allBillingKeyRegistered = groupBuy.getParticipants().stream()
            .allMatch(GroupBuyParticipant::isBillingKeyRegistered);
        if (!allBillingKeyRegistered) {
            throw new CustomException(CustomErrorCode.INVALID_REQUEST);
        }

        groupBuy.updateStatus(GroupBuyStatus.CONFIRMED);

        // 결제 요청 이벤트 발행
        eventPublisher.publishPaymentRequest(groupBuy);

        return GroupBuyResponse.from(groupBuy);
    }

    /**
     * 빌링키 등록 현황 조회
     */
    @Transactional(readOnly = true)
    public BillingKeyStatusResponse getBillingKeyStatus(Long groupBuyId, Long memberId) {
        GroupBuy groupBuy = groupBuyRepository.findByIdWithParticipants(groupBuyId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND));

        if (!groupBuy.getHost().getId().equals(memberId)) {
            throw new CustomException(CustomErrorCode.FORBIDDEN);
        }

        int totalParticipants = groupBuy.getCurrentParticipantCount();
        long registeredCount = groupBuy.getParticipants().stream()
            .filter(GroupBuyParticipant::isBillingKeyRegistered)
            .count();
        long pendingCount = totalParticipants - registeredCount;

        List<String> pendingParticipants = groupBuy.getParticipants().stream()
            .filter(p -> !p.isBillingKeyRegistered())
            .map(GroupBuyParticipant::getNickname)
            .toList();

        return new BillingKeyStatusResponse(
            totalParticipants,
            (int) registeredCount,
            (int) pendingCount,
            pendingParticipants,
            registeredCount == totalParticipants
        );
    }

    private void checkMinParticipantsReached(GroupBuy groupBuy) {
        if (groupBuy.isMinParticipantsReached()) {
            log.info("Minimum participants reached for group buy: groupBuyId={}",
                groupBuy.getId());
            eventPublisher.publishMinParticipantsReached(groupBuy);
        }
    }

    public record BillingKeyStatusResponse(
        int totalParticipants,
        int registeredCount,
        int pendingCount,
        List<String> pendingParticipants,
        boolean allRegistered
    ) {}
}
