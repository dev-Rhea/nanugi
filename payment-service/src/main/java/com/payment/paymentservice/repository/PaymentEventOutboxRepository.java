package com.payment.paymentservice.repository;

import com.payment.common.domain.PaymentEventOutbox;
import com.payment.common.domain.PaymentEventOutbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventOutboxRepository extends JpaRepository<PaymentEventOutbox, Long> {

    List<PaymentEventOutbox> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
        OutboxStatus status, LocalDateTime now);

    List<PaymentEventOutbox> findByGroupBuyId(Long groupBuyId);

    long countByStatus(OutboxStatus status);
}
