package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface OrderRepository extends JpaRepository<Order, Long> {
  Optional<Order> findByPagerColorAndPagerNumberAndStatusAndDeliveredAtIsNull(
      String var1, String var2, OrderStatus var3);

    @Query(value="SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findActiveOrders(@Param("status") OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status AND o.deliveredAt IS NULL")
    List<Order> findActiveOrdersWithItems(@Param("status") OrderStatus status);

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

    List<Order> findByStatusAndPaymentMethodIsNotNullAndCreatedAtBetween(
            OrderStatus status,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    Optional<Order> findFirstByOrderByCreatedAtAsc();

    @Query("SELECT MIN(o.createdAt) FROM Order o")
    Optional<LocalDateTime> findMinCreatedAt();

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items")
    List<Order> findAllWithItems();

    @Query("SELECT o FROM Order o ORDER BY o.idOrder DESC")
    Page<Order> findAllOrdersOnly(Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.idOrder < :afterId ORDER BY o.idOrder DESC")
    List<Order> findOrdersAfter(@Param("afterId") Long afterId, Pageable pageable);

}
