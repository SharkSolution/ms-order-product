package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.DiscountUsage;
import com.suresell.orders.domain.port.out.DiscountUsageRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DiscountUsageRepositoryAdapter implements DiscountUsageRepositoryPort {

    private final DiscountUsageRepository discountUsageRepository;

    @Override
    public DiscountUsage save(DiscountUsage discountUsage) {
        return discountUsageRepository.save(discountUsage);
    }

    @Override
    public Optional<DiscountUsage> findById(Long id) {
        return discountUsageRepository.findById(id);
    }

    @Override
    public List<DiscountUsage> findAll() {
        return discountUsageRepository.findAll();
    }

    @Override
    public Optional<DiscountUsage> findByOrderIdAndCouponId(Long orderId, Long couponId) {
        return discountUsageRepository.findByOrderIdAndCouponId(orderId, couponId);
    }

    @Override
    public List<DiscountUsage> findByOrderId(Long orderId) {
        return discountUsageRepository.findByOrderId(orderId);
    }

    @Override
    public List<DiscountUsage> findByCouponId(Long couponId) {
        return discountUsageRepository.findByCouponId(couponId);
    }
}
