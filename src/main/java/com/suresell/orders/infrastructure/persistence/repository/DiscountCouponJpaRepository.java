package com.suresell.orders.infrastructure.persistence.repository;

import com.suresell.orders.domain.model.DiscountCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscountCouponJpaRepository extends JpaRepository<DiscountCoupon, Long> {
    // Custom queries will need to be re-added if they existed in the original repository
}
