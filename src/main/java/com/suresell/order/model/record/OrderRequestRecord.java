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
 */
package com.suresell.order.model.record;
import com.suresell.order.model.enums.PagerColor;
import com.suresell.order.model.record.OrderItemRequestRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public record OrderRequestRecord(@NotBlank(message="El nombre/color es obligatorio") String pagerColor, @NotBlank(message="El n\u00famero es obligatorio") String pagerNumber, List<OrderItemRequestRecord> items, String discountCode, @NotBlank(message="El m\u00e9todo de pago es obligatorio") String paymentMethod) {

    public OrderRequestRecord(String pagerColor, String pagerNumber, List<OrderItemRequestRecord> items, String discountCode, String paymentMethod) {
        this.pagerColor = pagerColor;
        this.pagerNumber = pagerNumber;
        this.items = items;
        this.discountCode = discountCode;
        this.paymentMethod = paymentMethod;
    }
    @NotBlank(message="El nombre/color es obligatorio")
    public String pagerColor() {
        return this.pagerColor;
    }
    @NotBlank(message="El n\u00famero es obligatorio")
    public String pagerNumber() {
        return this.pagerNumber;
    }
    public List<OrderItemRequestRecord> items() {
        return this.items;
    }
    public String discountCode() {
        return this.discountCode;
    }
    @NotBlank(message="El m\u00e9todo de pago es obligatorio")
    public String paymentMethod() {
        return this.paymentMethod;
    }
}
