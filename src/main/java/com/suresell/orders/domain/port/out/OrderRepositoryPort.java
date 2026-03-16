package com.suresell.orders.domain.port.out;

import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findAll();
    Page<Order> findAll(Pageable pageable);
    Optional<Order> findOccupiedPagerOrder(
            String pagerColor, String pagerNumber, OrderStatus status);
    List<Order> findActiveOrders(OrderStatus status);
    List<Order> findActiveOrdersWithItems(OrderStatus status);
    List<Object[]> findTotalByPaymentMethodAndStatus(
            OrderStatus status, LocalDateTime startOfDay, LocalDateTime endOfDay);
    Optional<LocalDateTime> findMinCreatedAtByStatus(
            OrderStatus status, LocalDateTime startOfDay, LocalDateTime endOfDay);
    Integer countByStatus(
            OrderStatus status, LocalDateTime startOfDay, LocalDateTime endOfDay);
    Optional<Order> findFirstByOrderByCreatedAtAsc();
    Optional<LocalDateTime> findMinCreatedAt();
    List<Order> findAllWithItems();
    Page<Order> findAllOrdersOnly(String pagerColor, String pagerNumber, Long idOrder, Pageable pageable);
    List<Order> findOrdersAfter(Long afterId, Pageable pageable);
    List<Order> findByStatusAndPaymentMethodIsNotNullAndCreatedAtBetween(
            OrderStatus status, LocalDateTime startOfDay, LocalDateTime endOfDay);
}
