/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.enums.PagerColor
 *  com.suresell.order.model.record.PagerAvailabilityDto
 */
package com.suresell.order.model.record;

import com.suresell.order.model.enums.PagerColor;

public record PagerAvailabilityDto(PagerColor pagerColor, Integer pagerNumber, Boolean available, Long orderId) {
    private final PagerColor pagerColor;
    private final Integer pagerNumber;
    private final Boolean available;
    private final Long orderId;

    public PagerAvailabilityDto(PagerColor pagerColor, Integer pagerNumber, Boolean available, Long orderId) {
        this.pagerColor = pagerColor;
        this.pagerNumber = pagerNumber;
        this.available = available;
        this.orderId = orderId;
    }

    public PagerColor pagerColor() {
        return this.pagerColor;
    }

    public Integer pagerNumber() {
        return this.pagerNumber;
    }

    public Boolean available() {
        return this.available;
    }

    public Long orderId() {
        return this.orderId;
    }
}

