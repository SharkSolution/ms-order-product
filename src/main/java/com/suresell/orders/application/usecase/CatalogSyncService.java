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

    @Transactional
    public void syncActiveOrdersTrackingFromCloud() {
        if (cloudJdbcTemplate.isEmpty()) return;
        try {
            // 1. Obtener órdenes locales que están pagadas pero NO entregadas
            List<com.suresell.orders.domain.model.Order> activeOrders = orderRepository.findActiveOrdersWithItems(
                    com.suresell.orders.domain.model.OrderStatus.pagado, false);
            
            if (activeOrders.isEmpty()) return;

            List<Long> orderIds = activeOrders.stream()
                    .map(com.suresell.orders.domain.model.Order::getIdOrder)
                    .toList();

            // 2. Consultar Postgres solo por esas IDs
            JdbcTemplate cloud = cloudJdbcTemplate.get();
            String inSql = String.join(",", orderIds.stream().map(Object::toString).toList());
            String sql = "SELECT order_id, delivered, pager_returned, preparation_duration_seconds FROM order_delivery_tracking WHERE order_id IN (" + inSql + ") AND (delivered = true OR pager_returned = true)";
            
            cloud.query(sql, (rs) -> {
                Long orderId = rs.getLong("order_id");
                boolean delivered = rs.getBoolean("delivered");
                boolean pagerReturned = rs.getBoolean("pager_returned");
                int duration = rs.getInt("preparation_duration_seconds");

                orderDeliveryTrackingRepository.findById(orderId).ifPresent(localDt -> {
                    boolean changed = false;
                    if (localDt.getDelivered() != delivered) {
                        localDt.setDelivered(delivered);
                        localDt.setPreparationDurationSeconds(duration);
                        changed = true;
                    }
                    // Solo actualizamos local si en la nube ya se marcó como devuelto
                    if (!localDt.getPagerReturned() && pagerReturned) {
                        localDt.setPagerReturned(true);
                        changed = true;
                    }

                    if (changed) {
                        orderDeliveryTrackingRepository.save(localDt);
                        log.info("Orden #{} actualizada desde nube (Delivered: {}, PagerReturned: {}).", 
                                orderId, delivered, localDt.getPagerReturned());
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error en sincronización selectiva de tracking: {}", e.getMessage());
        }
    }

    private final com.suresell.orders.infrastructure.persistence.OrderRepository orderRepository;
    private final com.suresell.orders.infrastructure.persistence.OrderDeliveryTrackingRepository orderDeliveryTrackingRepository;
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
