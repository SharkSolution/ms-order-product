package com.suresell.orders.domain.model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(name = "menu_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.EntityListeners(com.suresell.orders.multitenant.TenantEntityListener.class)
public class MenuCategory implements com.suresell.orders.multitenant.TenantOwned {
    @Column(name = "tenant_id")
    private String tenantId;

    @Id
    @Column(name = "id_category", nullable = false, length = 255)
    private String idCategory;
    @Column(name = "name_category", nullable = false, length = 255)
    private String nameCategory;
    @OneToMany(mappedBy = "category")
    private List<MenuProduct> products = new ArrayList<>();
}
