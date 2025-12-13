/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DiscountCoupon
 *  com.suresell.order.repository.DiscountCouponRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.stereotype.Repository
 */
package com.suresell.order.repository;
import com.suresell.order.model.entity.DiscountCoupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DiscountCouponRepository
extends JpaRepository<DiscountCoupon, Long> {
    public Optional<DiscountCoupon> findByCode(String var1);
    @Query(value="SELECT d FROM DiscountCoupon d WHERE UPPER(d.code) = UPPER(?1)")
    public Optional<DiscountCoupon> findByCodeIgnoreCase(String var1);
    public List<DiscountCoupon> findByIsActive(Boolean var1);
    public boolean existsByCode(String var1);
}
