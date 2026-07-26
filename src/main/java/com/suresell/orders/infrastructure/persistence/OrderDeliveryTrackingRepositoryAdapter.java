package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.port.out.OrderDeliveryTrackingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class OrderDeliveryTrackingRepositoryAdapter implements OrderDeliveryTrackingRepositoryPort {
    private final OrderDeliveryTrackingRepository orderDeliveryTrackingRepository;
    @Override
    public OrderDeliveryTracking save(OrderDeliveryTracking tracking) {
        return orderDeliveryTrackingRepository.save(tracking);
    }

    @Override
    public boolean reabrirParaCocina(java.util.UUID orderUuid) {
        return orderDeliveryTrackingRepository.reabrirParaCocina(orderUuid) > 0;
    }
}
