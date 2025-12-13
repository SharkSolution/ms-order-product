/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DiscountUsage
 *  com.suresell.order.repository.DiscountUsageRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.stereotype.Repository
 */
package com.suresell.order.repository;
import com.suresell.order.model.entity.DiscountUsage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DiscountUsageRepository
extends JpaRepository<DiscountUsage, Long> {
    @Query(value="SELECT d FROM DiscountUsage d WHERE d.orderId = ?1 AND d.coupon.id = ?2")
    public Optional<DiscountUsage> findByOrderIdAndCouponId(Long var1, Long var2);
    public List<DiscountUsage> findByOrderId(Long var1);
    @Query(value="SELECT d FROM DiscountUsage d WHERE d.coupon.id = ?1")
    public List<DiscountUsage> findByCouponId(Long var1);
}
