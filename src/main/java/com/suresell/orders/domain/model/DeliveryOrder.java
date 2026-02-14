package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_order") // Assuming the table name is 'delivery_order'
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Assuming auto-increment for Long ID
    private Long id;
    @Column(name = "order_id", unique = true, nullable = false)
    private Integer orderId;
    @Column(name = "customer_name")
    private String customerName;
    private String building;
    @Column(name = "delivery_notes")
    private String deliveryNotes;
    @Column(name = "order_summary")
    private String orderSummary;
    private String phone;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Enumerated(EnumType.STRING) // Assuming DeliveryStatus is an enum
    @Column(name = "delivery_status")
    private DeliveryStatus deliveryStatus;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
