package com.suresell.orders.domain.model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
@Entity
@Table(name = "order_delivery_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "order")
public class OrderDeliveryTracking {
    @Id
    @Column(name = "order_id")
    private Long orderId;
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    private Order order;
    @Column(name = "delivered", nullable = false)
    private Boolean delivered = false;
    @Column(name = "preparation_duration_seconds")
    private Integer preparationDurationSeconds;
}
