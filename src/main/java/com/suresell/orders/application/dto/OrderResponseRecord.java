package com.suresell.orders.application.dto;
import com.suresell.orders.application.dto.OrderItemResponseRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Schema(description = "Respuesta detallada de una orden")
public record OrderResponseRecord(
    @Schema(description = "ID único de la orden", example = "101")
    Long idOrder, 
    @Schema(description = "Color del pager", example = "AZUL")
    String pagerColor, 
    @Schema(description = "Número del pager", example = "5")
    String pagerNumber, 
    @Schema(description = "Fecha y hora de creación")
    LocalDateTime createdAt, 
    @Schema(description = "Subtotal antes de descuentos", example = "45000")
    BigDecimal subtotal, 
    @Schema(description = "Total final después de descuentos", example = "40500")
    BigDecimal total, 
    @Schema(description = "Estado actual de la orden", example = "pagado")
    String status, 
    @Schema(description = "Método de pago", example = "CARD")
    String paymentMethod, 
    @Schema(description = "Código de descuento aplicado", example = "BIENVENIDA")
    String discountCode, 
    @Schema(description = "Porcentaje de descuento aplicado", example = "10")
    BigDecimal discountPercentage, 
    @Schema(description = "Monto del descuento aplicado", example = "4500")
    BigDecimal discountAmount, 
    @Schema(description = "Indica si la orden ya fue entregada", example = "false")
    Boolean delivered, 
    @Schema(description = "Indica si la orden ya se sincronizó con la nube", example = "true")
    Boolean synced,
    @Schema(description = "Duración de preparación en segundos", example = "300")
    Integer preparationDurationSeconds, 
    @Schema(description = "Items incluidos en la orden")
    List<OrderItemResponseRecord> items
) {
}
