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
    @Schema(description = "Método de pago", example = "CASH", allowableValues = {"CASH", "CARD", "NEQUI", "QR", "MIXED"})
    String paymentMethod,
    @Schema(description = "Multipago (F5): splits por medio; su suma debe igualar el total. Null/vacío = pago simple.")
    List<PaymentSplitRecord> payments,
    @Schema(description = "Clave de idempotencia generada por el cliente (N2/D1). Si llega una "
            + "orden con una clave ya registrada, se devuelve la existente en vez de crear otra. "
            + "Protege contra el doble POST del outbox del POS y contra reintentos por timeout.",
            example = "0f2b8c4e-2f1a-4e2a-9d3b-6d5f1c9a7e10")
    String idempotencyKey,
    @Schema(description = "N2 — omite la validación de disponibilidad del rastreador. "
            + "Lo usa la app de MESEROS: el mesero lleva el pedido a la mesa, no entrega con "
            + "rastreador, así que varias órdenes suyas pueden convivir sin ocupar uno. "
            + "El POS de plazoleta NO lo envía y sigue validando.", example = "false")
    Boolean skipPagerCheck
) {
    /** Split de multipago: método + monto. */
    public record PaymentSplitRecord(String method, java.math.BigDecimal amount) {
    }
}
