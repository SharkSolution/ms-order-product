/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.Order
 *  com.suresell.order.model.entity.OrderItem
 *  jakarta.persistence.Column
 *  jakarta.persistence.Entity
 *  jakarta.persistence.FetchType
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.JoinColumn
 *  jakarta.persistence.ManyToOne
 *  jakarta.persistence.Table
 *  lombok.Generated
 */
package com.suresell.order.model.entity;

import com.suresell.order.model.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Generated;

@Entity
@Table(name="order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_order_item")
    private Long idOrderItem;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_id")
    private Order order;
    @Column(name="product_id")
    private String productId;
    private int quantity;
    @Column(name="unit_price")
    private int unitPrice;
    @Column(name="total_price")
    private int totalPrice;
    private String instructions;

    @Generated
    public Long getIdOrderItem() {
        return this.idOrderItem;
    }

    @Generated
    public Order getOrder() {
        return this.order;
    }

    @Generated
    public String getProductId() {
        return this.productId;
    }

    @Generated
    public int getQuantity() {
        return this.quantity;
    }

    @Generated
    public int getUnitPrice() {
        return this.unitPrice;
    }

    @Generated
    public int getTotalPrice() {
        return this.totalPrice;
    }

    @Generated
    public String getInstructions() {
        return this.instructions;
    }

    @Generated
    public void setIdOrderItem(Long idOrderItem) {
        this.idOrderItem = idOrderItem;
    }

    @Generated
    public void setOrder(Order order) {
        this.order = order;
    }

    @Generated
    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Generated
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Generated
    public void setUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }

    @Generated
    public void setTotalPrice(int totalPrice) {
        this.totalPrice = totalPrice;
    }

    @Generated
    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OrderItem)) {
            return false;
        }
        OrderItem other = (OrderItem)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        if (this.getQuantity() != other.getQuantity()) {
            return false;
        }
        if (this.getUnitPrice() != other.getUnitPrice()) {
            return false;
        }
        if (this.getTotalPrice() != other.getTotalPrice()) {
            return false;
        }
        Long this$idOrderItem = this.getIdOrderItem();
        Long other$idOrderItem = other.getIdOrderItem();
        if (this$idOrderItem == null ? other$idOrderItem != null : !((Object)this$idOrderItem).equals(other$idOrderItem)) {
            return false;
        }
        Order this$order = this.getOrder();
        Order other$order = other.getOrder();
        if (this$order == null ? other$order != null : !this$order.equals(other$order)) {
            return false;
        }
        String this$productId = this.getProductId();
        String other$productId = other.getProductId();
        if (this$productId == null ? other$productId != null : !this$productId.equals(other$productId)) {
            return false;
        }
        String this$instructions = this.getInstructions();
        String other$instructions = other.getInstructions();
        return !(this$instructions == null ? other$instructions != null : !this$instructions.equals(other$instructions));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof OrderItem;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + this.getQuantity();
        result = result * 59 + this.getUnitPrice();
        result = result * 59 + this.getTotalPrice();
        Long $idOrderItem = this.getIdOrderItem();
        result = result * 59 + ($idOrderItem == null ? 43 : ((Object)$idOrderItem).hashCode());
        Order $order = this.getOrder();
        result = result * 59 + ($order == null ? 43 : $order.hashCode());
        String $productId = this.getProductId();
        result = result * 59 + ($productId == null ? 43 : $productId.hashCode());
        String $instructions = this.getInstructions();
        result = result * 59 + ($instructions == null ? 43 : $instructions.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "OrderItem(idOrderItem=" + this.getIdOrderItem() + ", order=" + String.valueOf(this.getOrder()) + ", productId=" + this.getProductId() + ", quantity=" + this.getQuantity() + ", unitPrice=" + this.getUnitPrice() + ", totalPrice=" + this.getTotalPrice() + ", instructions=" + this.getInstructions() + ")";
    }

    @Generated
    public OrderItem() {
    }

    @Generated
    public OrderItem(Long idOrderItem, Order order, String productId, int quantity, int unitPrice, int totalPrice, String instructions) {
        this.idOrderItem = idOrderItem;
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.instructions = instructions;
    }
}

