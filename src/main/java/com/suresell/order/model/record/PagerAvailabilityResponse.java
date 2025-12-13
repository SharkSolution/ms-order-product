/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.PagerAvailabilityDto
 *  com.suresell.order.model.record.PagerAvailabilityResponse
 */
package com.suresell.order.model.record;
import com.suresell.order.model.record.PagerAvailabilityDto;
import java.util.List;
public record PagerAvailabilityResponse(Integer totalPagers, Integer availablePagers, Integer occupiedPagers, List<PagerAvailabilityDto> pagers) {

    public PagerAvailabilityResponse(Integer totalPagers, Integer availablePagers, Integer occupiedPagers, List<PagerAvailabilityDto> pagers) {
        this.totalPagers = totalPagers;
        this.availablePagers = availablePagers;
        this.occupiedPagers = occupiedPagers;
        this.pagers = pagers;
    }
    public Integer totalPagers() {
        return this.totalPagers;
    }
    public Integer availablePagers() {
        return this.availablePagers;
    }
    public Integer occupiedPagers() {
        return this.occupiedPagers;
    }
    public List<PagerAvailabilityDto> pagers() {
        return this.pagers;
    }
}
