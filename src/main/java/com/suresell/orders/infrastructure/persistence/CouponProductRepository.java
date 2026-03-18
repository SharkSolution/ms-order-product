package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.CouponProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
public interface CouponProductRepository
extends JpaRepository<CouponProduct, Long> {
    @Transactional
    public void deleteByCouponId(Long var1);
}
