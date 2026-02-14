package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "order_item") // Assuming the table name is 'order_item'
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Assuming auto-increment for Long ID
    @Column(name = "id_order_item")
    private Long idOrderItem;

    @ManyToOne(fetch = FetchType.LAZY) // Many OrderItems can belong to one Order
    @JoinColumn(name = "order_id", nullable = false) // Foreign key column in order_item table
    private Order order;

    @Column(name = "product_id")
    private String productId;
    private int quantity;
    @Column(name = "unit_price")
    private int unitPrice;
    @Column(name = "total_price")
    private int totalPrice;
    private String instructions;
    @Column(name = "combo_group")
    private Integer comboGroup;
}
