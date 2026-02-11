package com.suresell.orders.domain.model;
import java.util.Arrays;
public enum OrderStatus {
    pagado("pagado");
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
