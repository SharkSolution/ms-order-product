package com.suresell.orders.application.dto;

import com.suresell.orders.domain.model.PagerColor;
import com.suresell.orders.application.dto.OrderItemRequestRecord;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record OrderRequestRecord(@NotBlank(message="El nombre/color es obligatorio") String pagerColor, @NotBlank(message="El n\u00famero es obligatorio") String pagerNumber, List<OrderItemRequestRecord> items, String discountCode, @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod) {
}
