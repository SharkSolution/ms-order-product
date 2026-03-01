package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import java.util.List;
public interface OrderItemRepositoryPort {
    OrderItem save(OrderItem orderItem);
    void deleteByOrder(Order order);
    List<OrderItem> findByOrderIds(List<Long> orderIds);
}
