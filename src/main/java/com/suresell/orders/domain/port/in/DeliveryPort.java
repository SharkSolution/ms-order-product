package com.suresell.orders.domain.port.in;

import com.suresell.orders.application.dto.CreateDeliveryOrderRequest;
import com.suresell.orders.domain.model.DeliveryOrder;

import java.util.List;

public interface DeliveryPort {
    DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request);
    List<DeliveryOrder> findPendingOrders();
    DeliveryOrder markAsDelivered(Integer orderId);
}
