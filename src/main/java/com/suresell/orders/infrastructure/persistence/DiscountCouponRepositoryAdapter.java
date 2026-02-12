package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.DiscountCoupon;
import com.suresell.orders.domain.port.out.DiscountCouponRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DiscountCouponRepositoryAdapter implements DiscountCouponRepositoryPort {

    private final DiscountCouponRepository discountCouponRepository;

    @Override
    public DiscountCoupon save(DiscountCoupon discountCoupon) {
        return discountCouponRepository.save(discountCoupon);
    }

    @Override
    public Optional<DiscountCoupon> findById(Long id) {
        return discountCouponRepository.findById(id);
    }

    @Override
    public List<DiscountCoupon> findAll() {
        return discountCouponRepository.findAll();
    }

    @Override
    public Optional<DiscountCoupon> findByCode(String code) {
        return discountCouponRepository.findByCode(code);
    }

    @Override
    public Optional<DiscountCoupon> findByCodeIgnoreCase(String code) {
        return discountCouponRepository.findByCodeIgnoreCase(code);
    }

    @Override
    public List<DiscountCoupon> findByIsActive(Boolean isActive) {
        return discountCouponRepository.findByIsActive(isActive);
    }

    @Override
    public boolean existsByCode(String code) {
        return discountCouponRepository.existsByCode(code);
    }

    @Override
    public void delete(DiscountCoupon discountCoupon) {
        discountCouponRepository.delete(discountCoupon);
    }
}
