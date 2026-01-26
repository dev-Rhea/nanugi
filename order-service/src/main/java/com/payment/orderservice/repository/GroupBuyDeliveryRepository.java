package com.payment.orderservice.repository;

import com.payment.common.domain.GroupBuyDelivery;
import com.payment.common.domain.GroupBuyDelivery.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyDeliveryRepository extends JpaRepository<GroupBuyDelivery, Long> {

    Optional<GroupBuyDelivery> findByGroupBuyId(Long groupBuyId);

    List<GroupBuyDelivery> findByStatus(DeliveryStatus status);
}
