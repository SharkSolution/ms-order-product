package com.suresell.orders.domain.model.printer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
@Schema(description = "Detalle de un producto en el ticket de impresión")
public record PosTicketItem(
        @Schema(description = "Nombre del producto", example = "Hamburguesa Especial")
        String name,
        @Schema(description = "Cantidad vendida", example = "2")
        int quantity,
        @Schema(description = "Precio unitario del producto", example = "15000")
        BigDecimal unitPrice,
        @Schema(description = "Total por este item (cantidad * unitPrice)", example = "30000")
        BigDecimal total
) {}
