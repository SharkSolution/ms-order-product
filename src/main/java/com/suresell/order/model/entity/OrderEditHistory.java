package com.suresell.orders.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEditHistory {
    private Long id;
    private Long orderId;
    private String editType;
    private String productId;
    private String productName;
    private Integer oldQuantity;
    private Integer newQuantity;
    private Integer oldTotal;
    private Integer newTotal;
    private LocalDateTime editedAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
