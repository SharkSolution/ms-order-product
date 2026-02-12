package com.suresell.orders.domain.service;

import com.suresell.orders.application.dto.CreateDeliveryOrderRequest;
import com.suresell.orders.domain.port.in.DeliveryPort;
import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.infrastructure.persistence.repository.DeliveryOrderJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveryOrderDomainService implements DeliveryPort {

    private final DeliveryOrderJpaRepository deliveryOrderJpaRepository;

    public DeliveryOrderDomainService(DeliveryOrderJpaRepository deliveryOrderJpaRepository) {
        this.deliveryOrderJpaRepository = deliveryOrderJpaRepository;
    }

    @Override
    public DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request) {
        // TODO: Implement logic
        return null;
    }

    @Override
    public List<DeliveryOrder> findPendingOrders() {
        // TODO: Implement logic
        return List.of();
    }

    @Override
    public DeliveryOrder markAsDelivered(Integer orderId) {
        // TODO: Implement logic
        return null;
    }
}
