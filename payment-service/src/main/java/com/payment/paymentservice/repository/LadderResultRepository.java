package com.payment.paymentservice.repository;

import com.payment.common.domain.LadderResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LadderResultRepository extends JpaRepository<LadderResult, Long> {

    Optional<LadderResult> findBySettlementId(Long settlementId);

    boolean existsBySettlementId(Long settlementId);
}
