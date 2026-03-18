package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.DiscountCoupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DiscountCouponRepository
extends JpaRepository<DiscountCoupon, Long> {
    public Optional<DiscountCoupon> findByCode(String var1);
    @Query(value="SELECT d FROM DiscountCoupon d WHERE UPPER(d.code) = UPPER(?1)")
    public Optional<DiscountCoupon> findByCodeIgnoreCase(String var1);
    public org.springframework.data.domain.Page<DiscountCoupon> findByIsActive(Boolean isActive, org.springframework.data.domain.Pageable pageable);
    @Query("SELECT d FROM DiscountCoupon d WHERE d.isActive = true AND (d.validFrom IS NULL OR d.validFrom <= :today) AND (d.validTo IS NULL OR d.validTo >= :today)")
    public org.springframework.data.domain.Page<DiscountCoupon> findCurrentlyActive(
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today, 
            org.springframework.data.domain.Pageable pageable);
    @Query("SELECT d FROM DiscountCoupon d WHERE d.validTo IS NOT NULL AND d.validTo < :today")
    public org.springframework.data.domain.Page<DiscountCoupon> findExpired(
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today, 
            org.springframework.data.domain.Pageable pageable);
    public boolean existsByCode(String var1);
}
