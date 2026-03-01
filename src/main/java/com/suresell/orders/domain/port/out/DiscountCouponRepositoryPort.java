package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.DiscountCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface DiscountCouponRepositoryPort {
    DiscountCoupon save(DiscountCoupon discountCoupon);
    Optional<DiscountCoupon> findById(Long id);
    List<DiscountCoupon> findAll();
    Page<DiscountCoupon> findAll(Pageable pageable);
    Optional<DiscountCoupon> findByCode(String code);
    Optional<DiscountCoupon> findByCodeIgnoreCase(String code);
    Page<DiscountCoupon> findByIsActive(Boolean isActive, Pageable pageable);
    Page<DiscountCoupon> findCurrentlyActive(LocalDate today, Pageable pageable);
    Page<DiscountCoupon> findExpired(LocalDate today, Pageable pageable);
    boolean existsByCode(String code);
    void delete(DiscountCoupon discountCoupon);
}
