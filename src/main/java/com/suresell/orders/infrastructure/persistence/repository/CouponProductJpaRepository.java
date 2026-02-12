package com.suresell.orders.infrastructure.persistence.repository;

import com.suresell.orders.domain.model.CouponProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponProductJpaRepository extends JpaRepository<CouponProduct, Long> {
    // Custom queries will need to be re-added if they existed in the original repository
}
