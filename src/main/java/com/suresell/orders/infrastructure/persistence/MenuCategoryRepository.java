package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.MenuCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, String> {
    @Query("SELECT DISTINCT c FROM MenuCategory c LEFT JOIN FETCH c.products ORDER BY c.nameCategory ASC")
    List<MenuCategory> findAllWithProducts();

    /**
     * Las categorías EN EL ORDEN QUE ELIGIÓ EL NEGOCIO.
     *
     * <p>`display_order` primero; las que no lo tengan van al final, por
     * nombre. Sin esto el orden dependía de lo que devolviera Postgres, que no
     * garantiza ninguno: dos llamadas podían traer el menú distinto.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM MenuCategory c
            ORDER BY CASE WHEN c.displayOrder IS NULL THEN 1 ELSE 0 END,
                     c.displayOrder,
                     c.nameCategory
            """)
    List<MenuCategory> findAllOrdenadas();
}
