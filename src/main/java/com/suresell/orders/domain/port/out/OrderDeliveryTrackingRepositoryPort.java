package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
public interface OrderDeliveryTrackingRepositoryPort {
    OrderDeliveryTracking save(OrderDeliveryTracking tracking);

    /**
     * N3/#2 — Devuelve la comanda a la cola de cocina. Se usa cuando una mesa que
     * ya fue marcada "lista" pide otra ronda: sin esto la orden queda
     * `delivered=true` y los platos nuevos NUNCA llegan a la cocina.
     *
     * @return true si había un tracking que reabrir.
     */
    boolean reabrirParaCocina(java.util.UUID orderUuid);
}
