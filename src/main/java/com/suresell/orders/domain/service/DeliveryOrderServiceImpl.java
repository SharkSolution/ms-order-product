package com.suresell.orders.domain.service;

import com.suresell.orders.application.dto.CreateDeliveryOrderRequest;
import com.suresell.orders.domain.model.DeliveryOrder;
import com.suresell.orders.domain.model.DeliveryStatus;
import com.suresell.orders.shared.exception.OrderAlreadyDeliveredException;
import com.suresell.orders.shared.exception.OrderIdAlreadyExistsException;
import com.suresell.orders.shared.exception.OrderNotFoundException;
import com.suresell.orders.domain.port.in.DeliveryOrderService;
import com.suresell.orders.domain.port.out.DeliveryOrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepositoryPort deliveryOrderRepositoryPort;

    @Override
    @Transactional
    public DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request) {
        if (deliveryOrderRepositoryPort.existsByOrderId(request.order_id())) {
            throw new OrderIdAlreadyExistsException("An operational record for order_id " + request.order_id() + " already exists.");
        }

        DeliveryOrder newOrder = new DeliveryOrder();
        newOrder.setOrderId(request.order_id());
        newOrder.setCustomerName(request.customer_name());
        newOrder.setBuilding(request.building());
        newOrder.setDeliveryNotes(request.delivery_notes());
        newOrder.setOrderSummary(request.order_summary());
        newOrder.setPhone(request.phone());
        newOrder.setPaymentMethod(request.payment_method());
        
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
