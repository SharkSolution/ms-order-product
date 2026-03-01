package com.suresell.orders.application.usecase;
import com.suresell.orders.domain.model.MenuCategory;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.infrastructure.persistence.MenuCategoryRepository;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogSyncService {
    private final MenuCategoryRepository categoryRepository;
    private final MenuProductRepository productRepository;
    @Qualifier("cloudJdbcTemplate")
    private final Optional<JdbcTemplate> cloudJdbcTemplate;
    @Transactional
    public void syncCatalogFromCloud() {
        if (cloudJdbcTemplate.isEmpty()) {
            log.warn("Sincronización de catálogo saltada: Cloud DataSource no está habilitado.");
            return;
        }
        try {
            log.info("Iniciando sincronización de catálogo desde la nube...");
            syncCategories();
            syncProducts();
            log.info("Sincronización de catálogo completada exitosamente.");
        } catch (Exception e) {
            log.error("Error crítico durante la sincronización de catálogo: {}", e.getMessage());
        }
    }
    private void syncCategories() {
        JdbcTemplate cloud = cloudJdbcTemplate.get();
        String sql = "SELECT id_category, name_category FROM menu_categories";
        List<MenuCategory> cloudCategories = cloud.query(sql, (rs, rowNum) -> {
            MenuCategory cat = new MenuCategory();
            cat.setIdCategory(rs.getString("id_category"));
            cat.setNameCategory(rs.getString("name_category"));
            return cat;
        });
        for (MenuCategory cloudCat : cloudCategories) {
            MenuCategory localCat = categoryRepository.findById(cloudCat.getIdCategory())
                    .orElse(new MenuCategory());
            localCat.setIdCategory(cloudCat.getIdCategory());
            localCat.setNameCategory(cloudCat.getNameCategory());
            categoryRepository.save(localCat);
        }
        log.info("Sincronizadas {} categorías.", cloudCategories.size());
    }
    private void syncProducts() {
        JdbcTemplate cloud = cloudJdbcTemplate.get();
        String sql = "SELECT id_product, name_product, price, active, category_id FROM menu_products";
        List<MenuProduct> cloudProducts = cloud.query(sql, (rs, rowNum) -> {
            MenuProduct prod = new MenuProduct();
            prod.setIdProduct(rs.getString("id_product"));
            prod.setNameProduct(rs.getString("name_product"));
            prod.setPrice(rs.getInt("price"));
            prod.setActive(rs.getBoolean("active"));
            String catId = rs.getString("category_id");
            if (catId != null) {
                MenuCategory cat = new MenuCategory();
                cat.setIdCategory(catId);
                prod.setCategory(cat);
            }
            return prod;
        });
        for (MenuProduct cloudProd : cloudProducts) {
            MenuProduct localProd = productRepository.findById(cloudProd.getIdProduct())
                    .orElse(new MenuProduct());
            localProd.setIdProduct(cloudProd.getIdProduct());
            localProd.setNameProduct(cloudProd.getNameProduct());
            localProd.setPrice(cloudProd.getPrice());
            localProd.setActive(cloudProd.getActive());
            if (cloudProd.getCategory() != null) {
                categoryRepository.findById(cloudProd.getCategory().getIdCategory())
                        .ifPresent(localProd::setCategory);
            }
            productRepository.save(localProd);
        }
        log.info("Sincronizados {} productos.", cloudProducts.size());
    }
}
