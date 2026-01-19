package com.payment.paymentservice.repository;

import com.payment.common.domain.PaymentStatus;
import com.payment.common.domain.SettlementParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementParticipantRepository extends JpaRepository<SettlementParticipant, Long> {

    List<SettlementParticipant> findBySettlementId(Long settlementId);

    List<SettlementParticipant> findBySettlementIdAndPaymentStatus(Long settlementId, PaymentStatus paymentStatus);

    Optional<SettlementParticipant> findBySettlementIdAndKakaoUuid(Long settlementId, String kakaoUuid);

    @Query("SELECT sp FROM SettlementParticipant sp WHERE sp.settlement.id = :settlementId AND sp.messageSent = false")
    List<SettlementParticipant> findUnnotifiedParticipants(@Param("settlementId") Long settlementId);

    @Query("SELECT COUNT(sp) FROM SettlementParticipant sp WHERE sp.settlement.id = :settlementId AND sp.paymentStatus = :status")
    long countBySettlementIdAndPaymentStatus(@Param("settlementId") Long settlementId, @Param("status") PaymentStatus status);

    @Query("SELECT sp FROM SettlementParticipant sp WHERE sp.member.id = :memberId")
    List<SettlementParticipant> findByMemberId(@Param("memberId") Long memberId);
}
