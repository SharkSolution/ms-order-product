/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.entity.OrderItem
 *  com.suresell.order.repository.OrderItemRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.suresell.order.repository;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    void deleteByOrder(Order order);

    // Query optimizada para traer items de múltiples órdenes en 1 sola query
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.idOrder IN :orderIds")
    List<OrderItem> findByOrderIds(@Param("orderIds") List<Long> orderIds);
}
