package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.domain.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    boolean existsByOrderId(Integer orderId);

    Optional<DeliveryOrder> findByOrderId(Integer orderId);

    List<DeliveryOrder> findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus status);
}
