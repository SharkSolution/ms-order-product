/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.enums.OrderStatus
 */
package com.suresell.order.model.enums;

import java.util.Arrays;

/*
 * Exception performing whole class analysis ignored.
 */
public enum OrderStatus {
    PAGADO("Pagado");

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

