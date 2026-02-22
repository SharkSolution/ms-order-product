package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "order_edit_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEditHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "edit_type")
    private String editType;
    @Column(name = "product_id")
    private String productId;
    @Column(name = "product_name")
    private String productName;
    @Column(name = "old_quantity")
    private Integer oldQuantity;
    @Column(name = "new_quantity")
    private Integer newQuantity;
    @Column(name = "old_total")
    private BigDecimal oldTotal;
    @Column(name = "new_total")
    private BigDecimal newTotal;
    @Column(name = "edited_at", updatable = false)
    private LocalDateTime editedAt;
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
}
