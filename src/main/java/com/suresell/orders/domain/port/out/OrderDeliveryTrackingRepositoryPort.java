package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
public interface OrderDeliveryTrackingRepositoryPort {
    OrderDeliveryTracking save(OrderDeliveryTracking tracking);
}
