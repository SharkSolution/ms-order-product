package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.MenuCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, String> {
    @Query("SELECT DISTINCT c FROM MenuCategory c LEFT JOIN FETCH c.products ORDER BY c.nameCategory ASC")
    List<MenuCategory> findAllWithProducts();
}
