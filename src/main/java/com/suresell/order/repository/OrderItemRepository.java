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

public interface OrderItemRepository
extends JpaRepository<OrderItem, Long> {
    public void deleteByOrder(Order var1);
}

