package com.suresell.order.serivices;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderEditHistory;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.model.record.OrderSyncResponse;
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

    /**
     * Sincroniza una orden de forma idempotente.
     * Si ya existe una orden con el mismo idempotencyKey, retorna la existente.
     * Si no existe, crea una nueva.
     */
    OrderSyncResponse syncOrderIdempotent(String idempotencyKey, OrderRequestRecord dto);

    /**
     * Busca una orden por idempotencyKey.
     * Usado para verificación post-timeout.
     */
    Order findByIdempotencyKey(String idempotencyKey);
}
