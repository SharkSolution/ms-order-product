package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface OrderRepository extends JpaRepository<Order, java.util.UUID> {
    Optional<Order> findByIdOrder(Long idOrder);

    @Query("SELECT o.idOrder FROM Order o WHERE o.uuidId = :uuidId")
    Optional<Long> findNumericIdByUuid(@Param("uuidId") java.util.UUID uuidId);

    @Query("""
            SELECT o
            FROM Order o
            LEFT JOIN o.deliveryTracking dt
            WHERE o.pagerColor = :pagerColor
              AND o.pagerNumber = :pagerNumber
              AND o.status = :status
              AND (dt IS NULL OR dt.delivered = :delivered)
            ORDER BY o.idOrder DESC
            """)
    List<Order> findOccupiedPagerOrders(
            @Param("pagerColor") String pagerColor,
            @Param("pagerNumber") String pagerNumber,
            @Param("status") OrderStatus status,
            @Param("delivered") Boolean delivered);
    @Query(value="SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findActiveOrders(@Param("status") OrderStatus status);
    @Query("""
            SELECT DISTINCT o
            FROM Order o
            LEFT JOIN FETCH o.items
            LEFT JOIN FETCH o.deliveryTracking dt
            WHERE o.status = :status
              AND (dt IS NULL OR dt.delivered = :delivered)
            """)
    List<Order> findActiveOrdersWithItems(@Param("status") OrderStatus status, @Param("delivered") Boolean delivered);
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
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items LEFT JOIN FETCH o.deliveryTracking")
    List<Order> findAllWithItems();
    @Query("SELECT o FROM Order o ORDER BY o.idOrder DESC")
    Page<Order> findAllOrdersOnly(Pageable pageable);
    @Query("""
            SELECT o FROM Order o 
            WHERE (:pagerColor IS NULL OR o.pagerColor = :pagerColor)
              AND (:pagerNumber IS NULL OR o.pagerNumber = :pagerNumber)
              AND (:idOrder IS NULL OR o.idOrder = :idOrder)
            ORDER BY o.idOrder DESC
            """)
    Page<Order> findWithFilters(
            @Param("pagerColor") String pagerColor,
            @Param("pagerNumber") String pagerNumber,
            @Param("idOrder") Long idOrder,
            Pageable pageable);
    @Query("SELECT o FROM Order o WHERE o.idOrder < :afterId ORDER BY o.idOrder DESC")
    List<Order> findOrdersAfter(@Param("afterId") Long afterId, Pageable pageable);
    @Modifying
    @Query("UPDATE Order o SET o.synced = true WHERE o.idOrder = :orderId")
    void markOrderAsSynced(@Param("orderId") Long orderId);
  @Query(value = "SELECT payment_method, SUM(total) " +
          "FROM orders " +
          "WHERE created_at BETWEEN :startTime AND :endTime " +
          "GROUP BY payment_method",
          nativeQuery = true)
  List<Object[]> sumTotalsByPaymentMethodAndSeller(
          @Param("startTime") Long startTime,
          @Param("endTime") Long endTimex
  );
}
