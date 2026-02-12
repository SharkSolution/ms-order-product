package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.DiscountCoupon;
import java.util.List;
import java.util.Optional;

public interface DiscountCouponRepositoryPort {
    DiscountCoupon save(DiscountCoupon discountCoupon);
    Optional<DiscountCoupon> findById(Long id);
    List<DiscountCoupon> findAll();
    Optional<DiscountCoupon> findByCode(String code);
    Optional<DiscountCoupon> findByCodeIgnoreCase(String code);
    List<DiscountCoupon> findByIsActive(Boolean isActive);
    boolean existsByCode(String code);
    void delete(DiscountCoupon discountCoupon);
}
