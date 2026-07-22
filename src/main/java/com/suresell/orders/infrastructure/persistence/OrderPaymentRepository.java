package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

    List<OrderPayment> findByOrderUuidId(UUID orderUuidId);

    /** Suma por método de los SPLITS en la ventana del cierre (RLS acota al tenant). */
    @Query("""
            SELECT p.method, SUM(p.amount) FROM OrderPayment p, Order o
            WHERE o.uuidId = p.orderUuidId
            AND o.paymentMethod = 'MIXED'
            AND o.createdAt BETWEEN :startTime AND :endTime
            GROUP BY p.method
            """)
    List<Object[]> sumSplitsByMethod(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
}
