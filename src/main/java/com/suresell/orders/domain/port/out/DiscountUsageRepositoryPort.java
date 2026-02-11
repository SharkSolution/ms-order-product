package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.DiscountUsage;
import java.util.List;
import java.util.Optional;

public interface DiscountUsageRepositoryPort {
    DiscountUsage save(DiscountUsage discountUsage);
    Optional<DiscountUsage> findById(Long id);
    List<DiscountUsage> findAll();
    Optional<DiscountUsage> findByOrderIdAndCouponId(Long orderId, Long couponId);
    List<DiscountUsage> findByOrderId(Long orderId);
    List<DiscountUsage> findByCouponId(Long couponId);
}
