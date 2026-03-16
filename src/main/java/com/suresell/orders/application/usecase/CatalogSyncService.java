package com.suresell.orders.application.usecase;
import com.suresell.orders.domain.model.MenuCategory;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.infrastructure.persistence.MenuCategoryRepository;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.OrderDeliveryTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);
    private final MenuCategoryRepository categoryRepository;
    private final MenuProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderDeliveryTrackingRepository orderDeliveryTrackingRepository;

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
            List<Order> activeOrders = orderRepository.findActiveOrdersWithItems(OrderStatus.pagado, false);
            
            if (activeOrders.isEmpty()) return;

            List<UUID> orderUuids = activeOrders.stream()
                    .map(Order::getUuidId)
                    .toList();

            // 2. Consultar Postgres por UUIDs
            JdbcTemplate cloud = cloudJdbcTemplate.get();
            String inSql = orderUuids.stream()
                    .map(uuid -> "'" + uuid.toString() + "'")
                    .collect(Collectors.joining(","));
            
            String sql = "SELECT order_id_uuid, delivered, pager_returned, preparation_duration_seconds FROM order_delivery_tracking WHERE order_id_uuid IN (" + inSql + ") AND (delivered = true OR pager_returned = true)";
            
            cloud.query(sql, (rs) -> {
                String uuidStr = rs.getString("order_id_uuid");
                UUID orderUuid = UUID.fromString(uuidStr);
                boolean delivered = rs.getBoolean("delivered");
                boolean pagerReturned = rs.getBoolean("pager_returned");
                int duration = rs.getInt("preparation_duration_seconds");

                // Buscamos por el UUID que es ahora la PK local también
                orderDeliveryTrackingRepository.findById(orderUuid).ifPresent(localDt -> {
                    boolean changed = false;
                    
                    if (Boolean.FALSE.equals(localDt.getDelivered()) && delivered) {
                        localDt.setDelivered(true);
                        localDt.setPreparationDurationSeconds(duration);
                        changed = true;
                    }
                    
                    if (Boolean.FALSE.equals(localDt.getPagerReturned()) && pagerReturned) {
                        localDt.setPagerReturned(true);
                        changed = true;
                    }

                    if (changed) {
                        orderDeliveryTrackingRepository.save(localDt);
                        log.info("Orden con UUID {} actualizada desde nube (Delivered: {}, PagerReturned: {}).", 
                                orderUuid, localDt.getDelivered(), localDt.getPagerReturned());
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error en sincronización selectiva de tracking: {}", e.getMessage());
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
