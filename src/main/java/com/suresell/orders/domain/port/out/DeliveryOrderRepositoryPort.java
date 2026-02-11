package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.domain.model.DeliveryStatus;
import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepositoryPort {
    DeliveryOrder save(DeliveryOrder deliveryOrder);
    Optional<DeliveryOrder> findById(Long id);
    List<DeliveryOrder> findAll();
    boolean existsByOrderId(Integer orderId);
    Optional<DeliveryOrder> findByOrderId(Integer orderId);
    List<DeliveryOrder> findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus status);
}
