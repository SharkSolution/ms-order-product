package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.CouponProduct;
import com.suresell.orders.domain.port.out.CouponProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class CouponProductRepositoryAdapter implements CouponProductRepositoryPort {
    private final CouponProductRepository couponProductRepository;
    @Override
    public CouponProduct save(CouponProduct couponProduct) {
        return couponProductRepository.save(couponProduct);
    }
    @Override
    public Optional<CouponProduct> findById(Long id) {
        return couponProductRepository.findById(id);
    }
    @Override
    public List<CouponProduct> findAll() {
        return couponProductRepository.findAll();
    }
    @Override
    @Transactional
    public void deleteByCouponId(Long couponId) {
        couponProductRepository.deleteByCouponId(couponId);
    }
}
