package com.suresell.order.mapper;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.rest_client.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ProductClient productClient;

    public OrderResponseRecord toOrderResponse(Order order) {
        List<OrderItemResponseRecord> items = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .toList();

        return new OrderResponseRecord(
                order.getIdOrder(),
                order.getPagerColor(),
                order.getPagerNumber(),
                order.getCreatedAt(),
                order.getSubtotal(),
                order.getTotal(),
                order.getStatus().getDisplayName(),
                order.getPaymentMethod(),
                order.getDiscountCode(),
                order.getDiscountPercentage(),
                order.getDiscountAmount(),
                order.getDeliveredAt(),
                order.getElapsedSecondsToDeliver(),
                items
        );
    }

    private OrderItemResponseRecord toOrderItemResponse(OrderItem item) {
        return new OrderItemResponseRecord(
                item.getProductId(),
                productClient.getProductName(item.getProductId()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getInstructions(),
                item.getComboGroup()
        );
    }
}
