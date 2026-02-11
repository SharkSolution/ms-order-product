package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.CouponProduct;
import java.util.Optional;
import java.util.List;

public interface CouponProductRepositoryPort {
    CouponProduct save(CouponProduct couponProduct);
    Optional<CouponProduct> findById(Long id);
    List<CouponProduct> findAll();
    void deleteByCouponId(Long couponId);
}
