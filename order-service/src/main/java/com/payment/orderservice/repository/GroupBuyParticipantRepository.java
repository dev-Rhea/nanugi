package com.payment.orderservice.repository;

import com.payment.common.domain.GroupBuyParticipant;
import com.payment.common.domain.GroupBuyParticipant.ParticipantPaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyParticipantRepository extends JpaRepository<GroupBuyParticipant, Long> {

    List<GroupBuyParticipant> findByGroupBuyId(Long groupBuyId);

    Optional<GroupBuyParticipant> findByGroupBuyIdAndKakaoUuid(Long groupBuyId, String kakaoUuid);

    Optional<GroupBuyParticipant> findByGroupBuyIdAndMemberId(Long groupBuyId, Long memberId);

    List<GroupBuyParticipant> findByGroupBuyIdAndPaymentStatus(Long groupBuyId,
                                                               ParticipantPaymentStatus paymentStatus);

    List<GroupBuyParticipant> findByGroupBuyIdAndBillingKeyRegistered(Long groupBuyId,
                                                                       boolean billingKeyRegistered);

    long countByGroupBuyId(Long groupBuyId);

    long countByGroupBuyIdAndPaymentStatus(Long groupBuyId, ParticipantPaymentStatus paymentStatus);

    boolean existsByGroupBuyIdAndKakaoUuid(Long groupBuyId, String kakaoUuid);
}
