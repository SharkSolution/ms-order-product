package com.suresell.orders.domain.model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(name = "menu_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class MenuProduct implements com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @Column(name = "id_product", nullable = false, length = 255)
    private String idProduct;
    @Column(name = "name_product", nullable = false, length = 255)
    private String nameProduct;
    @Column(name = "price", nullable = false)
    private Integer price;
    @Column(name = "active", nullable = false)
    private Boolean active;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MenuCategory category;
}
