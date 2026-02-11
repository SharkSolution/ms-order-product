package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.domain.model.DeliveryStatus;
import com.suresell.orders.domain.port.out.DeliveryOrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeliveryOrderRepositoryAdapter implements DeliveryOrderRepositoryPort {

    private final DeliveryOrderRepository deliveryOrderRepository;

    @Override
    public DeliveryOrder save(DeliveryOrder deliveryOrder) {
        return deliveryOrderRepository.save(deliveryOrder);
    }

    @Override
    public Optional<DeliveryOrder> findById(Long id) {
        return deliveryOrderRepository.findById(id);
    }

    @Override
    public List<DeliveryOrder> findAll() {
        return deliveryOrderRepository.findAll();
    }

    @Override
    public boolean existsByOrderId(Integer orderId) {
        return deliveryOrderRepository.existsByOrderId(orderId);
    }

    @Override
    public Optional<DeliveryOrder> findByOrderId(Integer orderId) {
        return deliveryOrderRepository.findByOrderId(orderId);
    }

    @Override
    public List<DeliveryOrder> findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus status) {
        return deliveryOrderRepository.findByDeliveryStatusOrderByCreatedAtAsc(status);
    }
}
