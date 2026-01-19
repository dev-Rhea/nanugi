package com.payment.paymentservice.repository;

import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByHostIdOrderByCreatedAtDesc(Long hostId);

    Optional<Settlement> findByShareToken(String shareToken);

    List<Settlement> findByHostIdAndStatus(Long hostId, SettlementStatus status);

    @Query("SELECT s FROM Settlement s LEFT JOIN FETCH s.participants WHERE s.id = :id")
    Optional<Settlement> findByIdWithParticipants(@Param("id") Long id);

    @Query("SELECT s FROM Settlement s LEFT JOIN FETCH s.participants WHERE s.shareToken = :shareToken")
    Optional<Settlement> findByShareTokenWithParticipants(@Param("shareToken") String shareToken);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.host.id = :hostId")
    long countByHostId(@Param("hostId") Long hostId);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.host.id = :hostId AND s.status = :status")
    long countByHostIdAndStatus(@Param("hostId") Long hostId, @Param("status") SettlementStatus status);
}
