/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ClosurePreviewResponse
 */
package com.suresell.order.model.record;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ClosurePreviewResponse(LocalDateTime openingTime, LocalDateTime currentTime, int totalOrders, String message) {
}
