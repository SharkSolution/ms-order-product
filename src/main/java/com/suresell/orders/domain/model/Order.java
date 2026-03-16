package com.suresell.orders.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")  
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    @Column(name = "id_order")
    private Long idOrder;
    @Column(name = "pager_color")
    private String pagerColor;
    @Column(name = "pager_number")
    private String pagerNumber;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    private BigDecimal subtotal;
    private BigDecimal total;
    @Enumerated(EnumType.STRING)  
    private OrderStatus status;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "discount_code")
    private String discountCode;
    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;
    @Column(name = "discount_amount")
    private BigDecimal discountAmount;
        @Column(name = "synced", nullable = false)
        private Boolean synced = false;
    
        @Column(name = "is_printed", nullable = false)
        private Boolean isPrinted = false;
    
        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)    
    private List<OrderItem> items;
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private OrderDeliveryTracking deliveryTracking;
}
