package com.suresell.orders.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Grupo de rastreadores configurable (N2/6.7). */
@Schema(description = "Grupo de rastreadores del negocio")
public record PagerGroupDto(
        @Schema(description = "Código estable; se guarda en las órdenes y NO se edita", example = "AMARILLO")
        String code,
        @Schema(description = "Nombre visible, editable", example = "Barra")
        String label,
        @Schema(description = "Color hex para la UI", example = "#eab308")
        String color,
        @Schema(description = "Cantidad de rastreadores del grupo", example = "16")
        Integer quantity) {
}
