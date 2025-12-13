package com.suresell.order.repository;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.enums.OrderStatus;
import com.suresell.order.model.enums.PagerColor;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  Optional<Order> findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(
      PagerColor var1, Integer var2, OrderStatus var3, String var4);

    @Query(value="SELECT o FROM Order o WHERE o.status = :status")
    public List<Order> findActiveOrders(@Param("status") OrderStatus status);

    @Query("SELECT o.paymentMethod, SUM(o.total) FROM Order o WHERE o.status = :status GROUP BY o.paymentMethod")
    List<Object[]> findTotalByPaymentMethodAndStatus(@Param("status") OrderStatus status);

    @Query("SELECT MIN(o.createdAt) FROM Order o WHERE o.status = :status")
    Optional<LocalDateTime> findMinCreatedAtByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Integer countByStatus(@Param("status") OrderStatus status);
}
