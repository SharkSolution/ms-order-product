package com.suresell.order.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "order_edit_history", indexes = {
    @Index(name = "idx_oeh_order_id", columnList = "order_id"),
    @Index(name = "idx_oeh_edited_at", columnList = "edited_at")
})
@Data
@NoArgsConstructor
public class OrderEditHistory {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /**
     * Tipo de edición: ITEM_ADDED, ITEM_REMOVED, ITEM_QUANTITY_CHANGED
     */
    @Column(name = "edit_type", nullable = false, length = 50)
    private String editType;

    @Column(name = "product_id", length = 50)
    private String productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "old_quantity")
    private Integer oldQuantity;

    @Column(name = "new_quantity")
    private Integer newQuantity;

    @Column(name = "old_total")
    private Integer oldTotal;

    @Column(name = "new_total")
    private Integer newTotal;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @PrePersist
    public void prePersist() {
        if (this.editedAt == null) {
            this.editedAt = LocalDateTime.now(BOGOTA_ZONE);
        }
    }
}
