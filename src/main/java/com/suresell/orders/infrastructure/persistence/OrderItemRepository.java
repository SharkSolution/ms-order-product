package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface OrderItemRepository extends JpaRepository<OrderItem, java.util.UUID> {
    void deleteByOrder(Order order);
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.idOrder IN :orderIds")
    List<OrderItem> findByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * N3/#1 — Marca como preparado todo lo pendiente de una orden.
     *
     * Va por ÍTEM: dentro de una misma mesa conviven platos ya despachados y
     * platos nuevos, así que marcar la orden entera perdería esa distinción.
     */
    @Modifying
    @Query("UPDATE OrderItem i SET i.preparedAt = :momento "
            + "WHERE i.orderId = :idOrder AND i.preparedAt IS NULL")
    int marcarPreparados(@Param("idOrder") Long idOrder,
                         @Param("momento") java.time.LocalDateTime momento);

}
