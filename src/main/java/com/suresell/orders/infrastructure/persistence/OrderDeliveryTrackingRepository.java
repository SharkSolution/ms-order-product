package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderDeliveryTrackingRepository extends JpaRepository<OrderDeliveryTracking, java.util.UUID> {

    /**
     * Cola FIFO de cocina (F4 Inc.1, docs/200): órdenes no entregadas ordenadas por
     * llegada. Mismo filtro que el ms-kitchen legacy (delivered=false AND
     * is_printed=false; en la práctica is_printed queda false en la nube). RLS
     * acota al tenant de la sesión.
     */
    @Query("""
            select distinct t
            from OrderDeliveryTracking t
            join fetch t.order o
            left join fetch o.items
            where t.delivered = false
            and o.isPrinted = false
            order by o.createdAt asc
            """)
    List<OrderDeliveryTracking> findActiveKitchenOrders();

    @Query(value = """
            select t
            from OrderDeliveryTracking t
            join t.order o
            where t.delivered = true
            order by o.createdAt desc
            """,
            countQuery = """
            select count(t)
            from OrderDeliveryTracking t
            where t.delivered = true
            """)
    Page<OrderDeliveryTracking> findDeliveredKitchenOrders(Pageable pageable);

    /**
     * N3/#2 — Reabre la comanda para cocina. UPDATE dirigido: cargar la entidad
     * y mutarla dentro del flujo de creación de orden arrastra la colección
     * `items` y revienta con "orphan deletion".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderDeliveryTracking t
               set t.delivered = false,
                   t.preparationDurationSeconds = null
             where t.orderIdUuid = :orderUuid
               and t.delivered = true
            """)
    int reabrirParaCocina(java.util.UUID orderUuid);
}
