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

    /**
     * Orden en que ESTE negocio quiere ver sus categorías.
     *
     * <p>Antes el orden lo decidía una lista de nombres escrita en la app
     * ("hamburguesas primero, después carnes al barril…"): la carta de un
     * cliente metida en el producto. Y el resto de las categorías salía en
     * orden NO DETERMINISTA, porque la consulta no ordenaba por nada.
     *
     * <p>Nulo = sin configurar: van al final, por nombre.
     */
    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Emoji de la categoría, elegido por el negocio.
     *
     * <p>Antes se deducía del nombre con una lista de palabras ('arepa',
     * 'barril', 'hamburguesa'…). Un negocio con otra carta se quedaba sin
     * íconos.
     */
    @Column(name = "icon")
    private String icon;
    @OneToMany(mappedBy = "category")
    private List<MenuProduct> products = new ArrayList<>();
}
