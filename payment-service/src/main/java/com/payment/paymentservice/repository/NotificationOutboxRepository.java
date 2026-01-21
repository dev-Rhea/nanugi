package com.payment.paymentservice.repository;

import com.payment.common.domain.NotificationOutbox;
import com.payment.common.domain.NotificationOutbox.NotificationType;
import com.payment.common.domain.NotificationOutbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
        OutboxStatus status, LocalDateTime now);

    List<NotificationOutbox> findBySettlementIdAndStatus(Long settlementId, OutboxStatus status);

    List<NotificationOutbox> findBySettlementId(Long settlementId);

    Optional<NotificationOutbox> findBySettlementIdAndNotificationTypeAndStatusIn(
        Long settlementId, NotificationType type, List<OutboxStatus> statuses);

    @Query("SELECT o FROM NotificationOutbox o WHERE o.status = :status AND o.nextRetryAt <= :now")
    List<NotificationOutbox> findPendingForRetry(@Param("status") OutboxStatus status,
                                                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE NotificationOutbox o SET o.status = :newStatus, o.updatedAt = :now " +
           "WHERE o.status = :oldStatus AND o.nextRetryAt <= :now")
    int markPendingAsProcessing(@Param("oldStatus") OutboxStatus oldStatus,
                                @Param("newStatus") OutboxStatus newStatus,
                                @Param("now") LocalDateTime now);

    long countByStatus(OutboxStatus status);

    long countBySettlementIdAndStatus(Long settlementId, OutboxStatus status);
}
