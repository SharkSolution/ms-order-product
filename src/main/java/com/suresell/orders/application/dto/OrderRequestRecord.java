package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.OrderItemRequestRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Solicitud para crear o actualizar una orden")
public record OrderRequestRecord(
    @NotBlank(message="El nombre/color es obligatorio") 
    @Schema(description = "Color del pager asignado", example = "AMARILLO")
    String pagerColor, 
    @NotBlank(message="El número es obligatorio") 
    @Schema(description = "Número del pager asignado", example = "15")
    String pagerNumber, 
    @Schema(description = "Lista de productos incluidos en la orden")
    List<OrderItemRequestRecord> items, 
    @Schema(description = "Código de descuento opcional", example = "DESC10")
    String discountCode, 
    @NotBlank(message="El método de pago es obligatorio") 
    @Schema(description = "Método de pago", example = "CASH", allowableValues = {"CASH", "CARD", "NEQUI", "QR"})
    String paymentMethod
) {
}
