package com.suresell.orders.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryOrderRequest {
    @NotNull
    private Integer order_id;
    @NotEmpty
    private String customer_name;
    private String building;
    private String delivery_notes;
    private String order_summary;
    @NotEmpty
    private String phone;
    @NotEmpty
    private String payment_method;

    // Explicit Getters
    public Integer getOrderId() {
        return order_id;
    }

    public String getCustomerName() {
        return customer_name;
    }

    public String getBuilding() {
        return building;
    }

    public String getDeliveryNotes() {
        return delivery_notes;
    }

    public String getOrderSummary() {
        return order_summary;
    }

    public String getPhone() {
        return phone;
    }

    public String getPaymentMethod() {
        return payment_method;
    }
}

