package com.payment.paymentservice.repository;

import com.payment.common.domain.GroupBuy;
import com.payment.common.domain.GroupBuyStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long> {

    List<GroupBuy> findByHost_IdOrderByCreatedAtDesc(Long hostId);

    List<GroupBuy> findByStatus(GroupBuyStatus status);

    Optional<GroupBuy> findByShareToken(String shareToken);

    @Query("SELECT g FROM GroupBuy g LEFT JOIN FETCH g.participants WHERE g.id = :id")
    Optional<GroupBuy> findByIdWithParticipants(@Param("id") Long id);

    @Query("SELECT g FROM GroupBuy g LEFT JOIN FETCH g.participants WHERE g.shareToken = :shareToken")
    Optional<GroupBuy> findByShareTokenWithParticipants(@Param("shareToken") String shareToken);

    @Query("SELECT g FROM GroupBuy g WHERE g.status = :status AND g.recruitmentDeadline < :now")
    List<GroupBuy> findExpiredRecruitments(@Param("status") GroupBuyStatus status,
                                            @Param("now") LocalDateTime now);

    @Query("SELECT g FROM GroupBuy g LEFT JOIN FETCH g.participants " +
           "WHERE g.host.id = :hostId ORDER BY g.createdAt DESC")
    List<GroupBuy> findByHostIdWithParticipants(@Param("hostId") Long hostId);

    long countByHost_IdAndStatus(Long hostId, GroupBuyStatus status);
}
