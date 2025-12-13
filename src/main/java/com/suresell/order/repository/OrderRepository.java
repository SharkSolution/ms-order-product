/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.enums.OrderStatus
 *  com.suresell.order.model.enums.PagerColor
 *  com.suresell.order.repository.OrderRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 */
package com.suresell.order.repository;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.enums.OrderStatus;
import com.suresell.order.model.enums.PagerColor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository
extends JpaRepository<Order, Long> {
    public Optional<Order> findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(PagerColor var1, Integer var2, OrderStatus var3, String var4);

    public List<Order> findByStatus(OrderStatus var1);

    @Query(value="SELECT o FROM Order o WHERE o.status = com.suresell.order.model.enums.OrderStatus.PAGADO AND o.deliveredAt = 'No'")
    public List<Order> findActiveOrders();
}

