/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.mapper.OrderMapper
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.entity.OrderItem
 *  com.suresell.order.model.record.OrderItemResponseRecord
 *  com.suresell.order.model.record.OrderResponseRecord
 *  com.suresell.order.rest_client.ProductClient
 *  lombok.Generated
 *  org.springframework.stereotype.Component
 */
package com.suresell.order.mapper;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.rest_client.ProductClient;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Component;
@Component
public class OrderMapper {
    private final ProductClient productClient;
    public OrderResponseRecord toOrderResponse(Order order) {
        List<OrderItemResponseRecord> items = order.getItems().stream().map(arg_0 -> this.toOrderItemResponse(arg_0)).toList();
        return new OrderResponseRecord(order.getIdOrder(), order.getPagerColor(), order.getPagerNumber(), order.getCreatedAt(), order.getSubtotal(), order.getTotal(), order.getStatus().getDisplayName(), order.getPaymentMethod(), order.getDiscountCode(), order.getDiscountPercentage(), order.getDiscountAmount(), order.getDeliveredAt(), order.getElapsedSecondsToDeliver(), items);
    }
    private OrderItemResponseRecord toOrderItemResponse(OrderItem item) {
        return new OrderItemResponseRecord(item.getProductId(), this.productClient.getProductName(item.getProductId()), item.getQuantity(), item.getUnitPrice(), item.getTotalPrice(), item.getInstructions());
    }
    @Generated
    public OrderMapper(ProductClient productClient) {
        this.productClient = productClient;
    }
}
