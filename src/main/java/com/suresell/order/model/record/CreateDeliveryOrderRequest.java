package com.suresell.order.model.record;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
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
