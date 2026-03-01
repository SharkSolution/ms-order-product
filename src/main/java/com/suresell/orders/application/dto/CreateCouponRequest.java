package com.suresell.orders.application.dto;

import com.suresell.orders.application.dto.ProductDiscountDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Solicitud para crear un nuevo cupón de descuento")
public record CreateCouponRequest(
    @Schema(description = "Código único del cupón", example = "PROMO2024")
    String code, 
    @Schema(description = "Nombre descriptivo del cupón", example = "Descuento de Bienvenida")
    String name, 
    @Schema(description = "Descripción detallada", example = "Aplica para todos los productos de la categoría Hamburguesas")
    String description, 
    @Schema(description = "Porcentaje de descuento", example = "10.0")
    BigDecimal discountPercentage, 
    @Schema(description = "Lista de productos específicos a los que aplica el cupón")
    List<ProductDiscountDto> products, 
    @Schema(description = "Fecha de inicio de validez", example = "2024-01-01")
    LocalDate validFrom, 
    @Schema(description = "Fecha de fin de validez", example = "2024-12-31")
    LocalDate validTo, 
    @Schema(description = "Días de la semana válidos (separados por coma)", example = "LUN,MAR,MIE")
    String validWeekdays, 
    @Schema(description = "Indica si el cupón está activo", example = "true")
    Boolean isActive) {
}
