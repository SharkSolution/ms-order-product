package com.suresell.order.repository;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    void deleteByOrder(Order order);
}
