package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.DiscountUsage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DiscountUsageRepository
extends JpaRepository<DiscountUsage, Long> {
    @Query(value="SELECT d FROM DiscountUsage d WHERE d.orderId = ?1 AND d.coupon.id = ?2")
    public Optional<DiscountUsage> findByOrderIdAndCouponId(Long var1, Long var2);
    public List<DiscountUsage> findByOrderId(Long var1);
    @Query(value="SELECT d FROM DiscountUsage d WHERE d.coupon.id = ?1")
    public List<DiscountUsage> findByCouponId(Long var1);
}
