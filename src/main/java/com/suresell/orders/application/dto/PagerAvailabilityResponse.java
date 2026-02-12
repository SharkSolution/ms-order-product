package com.suresell.orders.application.dto;

import java.util.List;

public record PagerAvailabilityResponse(List<PagerAvailabilityDto> availablePagers, List<PagerAvailabilityDto> occupiedPagers) {
}
