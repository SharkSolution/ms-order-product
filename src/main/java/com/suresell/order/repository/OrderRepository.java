package com.suresell.order.repository;

import com.suresell.order.model.entity.Order;
import com.suresell.order.model.enums.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  Optional<Order> findByPagerColorAndPagerNumberAndStatusAndDeliveredAt(
      String var1, String var2, OrderStatus var3, String var4);

    @Query(value="SELECT o FROM Order o WHERE o.status = :status")
    public List<Order> findActiveOrders(@Param("status") OrderStatus status);

    @Query("SELECT o.paymentMethod, SUM(o.total) FROM Order o WHERE o.status = :status AND o.createdAt BETWEEN :startOfDay AND :endOfDay GROUP BY o.paymentMethod")
    List<Object[]> findTotalByPaymentMethodAndStatus(
            @Param("status") OrderStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT MIN(o.createdAt) FROM Order o WHERE o.status = :status AND o.createdAt BETWEEN :startOfDay AND :endOfDay")
    Optional<LocalDateTime> findMinCreatedAtByStatus(
            @Param("status") OrderStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt BETWEEN :startOfDay AND :endOfDay")
    Integer countByStatus(
            @Param("status") OrderStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    Optional<Order> findFirstByOrderByCreatedAtAsc();

    @Query("SELECT MIN(o.createdAt) FROM Order o")
    Optional<LocalDateTime> findMinCreatedAt();
}
