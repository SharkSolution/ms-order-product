/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.CouponProduct
 *  com.suresell.order.repository.CouponProductRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.transaction.annotation.Transactional
 */
package com.suresell.order.repository;

import com.suresell.order.model.entity.CouponProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CouponProductRepository
extends JpaRepository<CouponProduct, Long> {
    @Transactional
    public void deleteByCouponId(Long var1);
}

