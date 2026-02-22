package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.MenuProduct;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuProductRepository extends JpaRepository<MenuProduct, String> {

    @Query("SELECT p FROM MenuProduct p LEFT JOIN FETCH p.category ORDER BY p.nameProduct ASC")
    List<MenuProduct> findAllWithCategory();

    @Query("SELECT p FROM MenuProduct p LEFT JOIN FETCH p.category WHERE p.idProduct IN :productIds")
    List<MenuProduct> findByIdProductInWithCategory(Collection<String> productIds);
}
