package com.suresell.order.serivices;

import com.suresell.order.model.record.CreateDeliveryOrderRequest;
import com.suresell.order.model.entity.DeliveryOrder;

import java.util.List;

public interface DeliveryOrderService {
    DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request);
    List<DeliveryOrder> findPendingOrders();
    DeliveryOrder markAsDelivered(Integer orderId);
}
