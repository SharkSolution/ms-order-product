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
            AND o.status <> com.suresell.orders.domain.model.OrderStatus.abierta
            AND o.createdAt BETWEEN :startTime AND :endTime
            GROUP BY p.method
            """)
    List<Object[]> sumSplitsByMethod(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * Splits de las ordenes MIXED, por MESERO y metodo.
     *
     * Sin esto una venta mixta entera caeria bajo la etiqueta "MIXED" y la
     * cajera no veria su parte en efectivo, que es justo el numero que usa para
     * saber cuanto recibir de cada mesero.
     */
    @Query("""
            SELECT o.waiterId, p.method, SUM(p.amount)
            FROM OrderPayment p, Order o
            WHERE o.uuidId = p.orderUuidId
            AND o.paymentMethod = 'MIXED'
            AND o.status <> com.suresell.orders.domain.model.OrderStatus.abierta
            AND o.createdAt BETWEEN :startTime AND :endTime
            GROUP BY o.waiterId, p.method
            """)
    List<Object[]> sumSplitsByWaiterAndMethod(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * Splits de las MIXED de UN TURNO, por metodo.
     *
     * <p>El resumen del turno se arma por sesion de mesero, no por ventana de
     * tiempo: un turno puede cruzar la medianoche y dos meseros pueden tener
     * turnos solapados. Por eso no sirve {@link #sumSplitsByWaiterAndMethod}.
     *
     * <p>Sin esto, una venta mixta entera caia bajo la etiqueta "MIXED" y su
     * parte en EFECTIVO —que el mesero tiene fisicamente en la mano— no entraba
     * en el efectivo esperado. El mesero entregaba mas de lo que el sistema le
     * pedia y la diferencia le aparecia como sobrante.
     */
    @Query("""
            SELECT p.method, SUM(p.amount)
            FROM OrderPayment p, Order o
            WHERE o.uuidId = p.orderUuidId
            AND o.paymentMethod = 'MIXED'
            AND o.status <> com.suresell.orders.domain.model.OrderStatus.abierta
            AND o.waiterSessionId = :sessionId
            GROUP BY p.method
            """)
    List<Object[]> sumSplitsByWaiterSession(@Param("sessionId") UUID sessionId);
}
