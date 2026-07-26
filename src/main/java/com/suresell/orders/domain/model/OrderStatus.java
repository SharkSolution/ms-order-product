package com.suresell.orders.domain.model;
import java.util.Arrays;
public enum OrderStatus {
    pagado("pagado"),
    /**
     * Modo Restaurante: consumo en curso, TODAVÍA NO COBRADO.
     *
     * Ojo al agregar consultas: una orden `abierta` NO es venta hasta que se
     * cobra la mesa. La cocina sí debe verla (se prepara igual), pero el cierre
     * de caja debe excluirla.
     */
    abierta("abierta");

    private final String displayName;
    private OrderStatus(String displayName) {
        this.displayName = displayName;
    }
    public String getDisplayName() {
        return this.displayName;
    }
    public static OrderStatus fromString(String text) {
        return Arrays.stream(OrderStatus.values()).filter(status -> status.name().equalsIgnoreCase(text) || status.getDisplayName().equalsIgnoreCase(text)).findFirst().orElseThrow(() -> new IllegalArgumentException("Cannot find a value for " + text));
    }
}
