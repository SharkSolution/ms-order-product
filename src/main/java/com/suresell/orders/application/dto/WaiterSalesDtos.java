package com.suresell.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Contrato de "ventas por mesero" del cierre de caja.
 *
 * Mismos nombres de campo que el `WaiterSalesResponse` de ms-core-app, para que
 * el POS solo tenga que cambiar la URL: la pantalla ya existe y funciona.
 */
public final class WaiterSalesDtos {

    private WaiterSalesDtos() {
    }

    public record WaiterSalesItem(
            Long waiterId,
            String waiterName,
            long ordersCount,
            BigDecimal total,
            Map<String, BigDecimal> breakdown
    ) {
    }

    public record WaiterSalesResponse(
            LocalDate date,
            BigDecimal grandTotal,
            long totalOrders,
            Map<String, BigDecimal> grandTotalByMethod,
            List<WaiterSalesItem> waiters,
            /** Ventas hechas desde caja (sin mesero). `null` si no hubo. */
            WaiterSalesItem unassigned
    ) {
    }
}
