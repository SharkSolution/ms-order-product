package com.suresell.order.serivices.impl;

import com.suresell.order.model.record.CreateDeliveryOrderRequest;
import com.suresell.order.model.entity.DeliveryOrder;
import com.suresell.order.model.enums.DeliveryStatus;
import com.suresell.order.exception.OrderAlreadyDeliveredException;
import com.suresell.order.exception.OrderIdAlreadyExistsException;
import com.suresell.order.exception.OrderNotFoundException;
import com.suresell.order.repository.DeliveryOrderRepository;
import com.suresell.order.serivices.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;

    @Override
    @Transactional
    public DeliveryOrder createDeliveryOrder(CreateDeliveryOrderRequest request) {
        // Regla: order_id NO puede repetirse
        if (deliveryOrderRepository.existsByOrderId(request.getOrder_id())) {
            throw new OrderIdAlreadyExistsException("An operational record for order_id " + request.getOrder_id() + " already exists.");
        }

        DeliveryOrder newOrder = new DeliveryOrder();
        newOrder.setOrderId(request.getOrder_id());
        newOrder.setCustomerName(request.getCustomer_name());
        newOrder.setBuilding(request.getBuilding());
        newOrder.setDeliveryNotes(request.getDelivery_notes());
        newOrder.setOrderSummary(request.getOrder_summary());
        newOrder.setPhone(request.getPhone());
        newOrder.setPaymentMethod(request.getPayment_method());
        
        // Reglas: siempre PENDING y delivered_at null al crear
        newOrder.setDeliveryStatus(DeliveryStatus.PENDING);
        newOrder.setDeliveredAt(null);

        return deliveryOrderRepository.save(newOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOrder> findPendingOrders() {
        return deliveryOrderRepository.findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING);
    }

    @Override
    @Transactional
    public DeliveryOrder markAsDelivered(Integer orderId) {
        // Regla: Si la orden no existe -> 404
        DeliveryOrder order = deliveryOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order with order_id " + orderId + " not found."));

        // Regla: Si ya está en DELIVERED -> 409
        if (order.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            throw new OrderAlreadyDeliveredException("Order " + orderId + " has already been marked as delivered.");
        }

        // Reglas: Cambiar estado y asignar fecha de entrega
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        return deliveryOrderRepository.save(order);
    }
}
