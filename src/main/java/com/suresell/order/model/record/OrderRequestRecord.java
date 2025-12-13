/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.enums.PagerColor
 *  com.suresell.order.model.record.OrderItemRequestRecord
 *  com.suresell.order.model.record.OrderRequestRecord
 *  jakarta.validation.constraints.Max
 *  jakarta.validation.constraints.Min
 *  jakarta.validation.constraints.NotBlank
 *  jakarta.validation.constraints.NotNull
 */
package com.suresell.order.model.record;

import com.suresell.order.model.enums.PagerColor;
import com.suresell.order.model.record.OrderItemRequestRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequestRecord(@NotNull(message="El color del pager es obligatorio") @NotNull(message="El color del pager es obligatorio") PagerColor pagerColor, @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") Integer pagerNumber, List<OrderItemRequestRecord> items, String discountCode, @NotBlank(message="El m\u00e9todo de pago es obligatorio") @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod) {
    @NotNull(message="El color del pager es obligatorio")
    private final @NotNull(message="El color del pager es obligatorio") PagerColor pagerColor;
    @NotNull(message="El n\u00famero del pager es obligatorio")
    @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1")
    @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16")
    private final @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") Integer pagerNumber;
    private final List<OrderItemRequestRecord> items;
    private final String discountCode;
    @NotBlank(message="El m\u00e9todo de pago es obligatorio")
    private final @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod;

    public OrderRequestRecord(@NotNull(message="El color del pager es obligatorio") @NotNull(message="El color del pager es obligatorio") PagerColor pagerColor, @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") Integer pagerNumber, List<OrderItemRequestRecord> items, String discountCode, @NotBlank(message="El m\u00e9todo de pago es obligatorio") @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod) {
        this.pagerColor = pagerColor;
        this.pagerNumber = pagerNumber;
        this.items = items;
        this.discountCode = discountCode;
        this.paymentMethod = paymentMethod;
    }

    @NotNull(message="El color del pager es obligatorio")
    public @NotNull(message="El color del pager es obligatorio") PagerColor pagerColor() {
        return this.pagerColor;
    }

    @NotNull(message="El n\u00famero del pager es obligatorio")
    @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1")
    @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16")
    public @NotNull(message="El n\u00famero del pager es obligatorio") @Min(value=1L, message="El n\u00famero del pager debe ser m\u00ednimo 1") @Max(value=16L, message="El n\u00famero del pager debe ser m\u00e1ximo 16") Integer pagerNumber() {
        return this.pagerNumber;
    }

    public List<OrderItemRequestRecord> items() {
        return this.items;
    }

    public String discountCode() {
        return this.discountCode;
    }

    @NotBlank(message="El m\u00e9todo de pago es obligatorio")
    public @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod() {
        return this.paymentMethod;
    }
}

