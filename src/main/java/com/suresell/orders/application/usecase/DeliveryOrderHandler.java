package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.CreateDeliveryOrderRequest;
import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.domain.model.DeliveryStatus;
import com.suresell.orders.shared.exception.OrderAlreadyDeliveredException;
import com.suresell.orders.shared.exception.OrderIdAlreadyExistsException;
import com.suresell.orders.shared.exception.OrderNotFoundException;
import com.suresell.orders.domain.port.in.DeliveryPort;
import com.suresell.orders.domain.port.out.DeliveryOrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class DeliveryOrderHandler implements DeliveryPort {

    private final DeliveryOrderRepositoryPort deliveryOrderRepositoryPort;

    @Override
    @Transactional
    public DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request) {
        if (deliveryOrderRepositoryPort.existsByOrderId(request.getOrderId())) {
            throw new OrderIdAlreadyExistsException("An operational record for order_id " + request.getOrderId() + " already exists.");
        }

        DeliveryOrder newOrder = new DeliveryOrder();
        newOrder.setOrderId(request.getOrderId());
        newOrder.setCustomerName(request.getCustomerName());
        newOrder.setBuilding(request.getBuilding());
        newOrder.setDeliveryNotes(request.getDeliveryNotes());
        newOrder.setOrderSummary(request.getOrderSummary());
        newOrder.setPhone(request.getPhone());
        newOrder.setPaymentMethod(request.getPaymentMethod());
        
        newOrder.setDeliveryStatus(DeliveryStatus.PENDING);
        newOrder.setDeliveredAt(null);

        return deliveryOrderRepositoryPort.save(newOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrder> findPendingOrders() {
        return deliveryOrderRepositoryPort.findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING);
    }

    @Override
    @Transactional
    public DeliveryOrder markAsDelivered(Integer orderId) {
        DeliveryOrder order = deliveryOrderRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order with order_id " + orderId + " not found."));

        if (order.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            throw new OrderAlreadyDeliveredException("Order " + orderId + " has already been marked as delivered.");
        }

        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        return deliveryOrderRepositoryPort.save(order);
    }
}
