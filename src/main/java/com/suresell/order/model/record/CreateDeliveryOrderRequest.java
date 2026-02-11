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
}
