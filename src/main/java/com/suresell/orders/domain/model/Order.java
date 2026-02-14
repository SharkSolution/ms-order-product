package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders") // Assuming the table name is 'orders'
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Assuming auto-increment for Long ID
    @Column(name = "id_order")
    private Long idOrder;
    @Column(name = "pager_color")
    private String pagerColor;
    @Column(name = "pager_number")
    private String pagerNumber;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "delivered_at")
    private String deliveredAt;
    private int subtotal;
    private int total;
    @Enumerated(EnumType.STRING) // Assuming OrderStatus is an enum
    private OrderStatus status;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "discount_code")
    private String discountCode;
    @Column(name = "discount_percentage")
    private Double discountPercentage;
    @Column(name = "discount_amount")
    private Integer discountAmount;
    @Column(name = "elapsed_seconds_to_deliver")
    private Integer elapsedSecondsToDeliver;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) // Assuming a one-to-many relationship with OrderItem
    private List<OrderItem> items;
}
