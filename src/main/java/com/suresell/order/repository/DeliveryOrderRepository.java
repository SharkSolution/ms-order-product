package com.suresell.order.repository;

import com.suresell.order.model.entity.DeliveryOrder;
import com.suresell.order.model.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    /**
     * Verifica si ya existe un registro con el order_id dado.
     * Es más eficiente que buscar la entidad completa.
     */
    boolean existsByOrderId(Integer orderId);

    /**
     * Busca una orden operativa por su order_id.
     */
    Optional<DeliveryOrder> findByOrderId(Integer orderId);

    /**
     * Retorna todas las órdenes con un estado específico, ordenadas por fecha de creación ascendente.
     */
    List<DeliveryOrder> findByDeliveryStatusOrderByCreatedAtAsc(DeliveryStatus status);
}
