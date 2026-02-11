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
    public List<DiscountCoupon> findByIsActive(Boolean var1);
    public boolean existsByCode(String var1);
}
