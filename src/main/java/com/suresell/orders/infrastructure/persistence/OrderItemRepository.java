package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface OrderItemRepository extends JpaRepository<OrderItem, java.util.UUID> {
    void deleteByOrder(Order order);
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.idOrder IN :orderIds")
    List<OrderItem> findByOrderIds(@Param("orderIds") List<Long> orderIds);
}
