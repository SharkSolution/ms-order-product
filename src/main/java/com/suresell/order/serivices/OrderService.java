package com.suresell.orders.domain.port.in;

import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderEditHistory;
import com.suresell.orders.application.dto.OrderRequestRecord;
import com.suresell.orders.application.dto.OrderResponseRecord;
import com.suresell.orders.application.dto.OrderSyncResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

    Order createOrUpdateOrder(OrderRequestRecord dto);

    List<OrderResponseRecord> getKitchenOrders();

    List<OrderResponseRecord> getAllOrders();

    Page<OrderResponseRecord> getAllOrdersPaginated(int page, int size);

    List<OrderResponseRecord> getAllOrdersKeyset(Long afterId, int size);

    OrderResponseRecord getOrderById(Long orderId);

    void updateStatus(Long orderId, String status);

    void updateOrder(Long orderId, OrderRequestRecord dto);

    List<OrderResponseRecord> getSalesReport();

    void updatePaymentMethod(Long orderId, String paymentMethod);

    OrderResponseRecord applyDiscountToOrder(Long orderId, String discountCode);

    void markAsDelivered(Long orderId, Integer elapsedSeconds);

    Page<OrderEditHistory> getOrderEditHistory(Long orderId, String adminPassword, int page, int size);

    OrderSyncResponse syncOrderIdempotent(String idempotencyKey, OrderRequestRecord dto);

    Order findByIdempotencyKey(String idempotencyKey);
}
