package com.suresell.order.serivices;

import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.record.OrderResponseRecord;

import java.util.List;

public interface OrderService {

    void createOrUpdateOrder(OrderRequestRecord dto);
    List<OrderResponseRecord> getKitchenOrders();
    List<OrderResponseRecord> getAllOrders();
    void updateStatus(Long orderId, String newStatus);
}
