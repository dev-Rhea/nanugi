package com.payment.paymentservice.repository;

import com.payment.paymentservice.domain.PaymentBatchItem;
import com.payment.paymentservice.domain.PaymentBatchItem.PaymentBatchItemStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentBatchItemRepository extends JpaRepository<PaymentBatchItem, Long> {

    Optional<PaymentBatchItem> findByGroupBuyIdAndParticipantId(Long groupBuyId, Long participantId);

    List<PaymentBatchItem> findByGroupBuyId(Long groupBuyId);

    /**
     * Spring Batch RepositoryItemReader가 Pageable을 넘겨 호출한다.
     * (Spring Batch 6의 RepositoryItemReader는 반환형이 Slice여야 한다 — List를
     * 반환하면 "ArrayList를 Slice로 캐스팅할 수 없다"는 ClassCastException이 난다.)
     */
    Slice<PaymentBatchItem> findByGroupBuyIdAndStatus(
        Long groupBuyId, PaymentBatchItemStatus status, Pageable pageable);
}
