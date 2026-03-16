package com.suresell.orders.application.usecase;
import com.suresell.orders.domain.model.MenuCategory;
import com.suresell.orders.domain.model.MenuProduct;
import com.suresell.orders.domain.model.Order;
import com.suresell.orders.domain.model.OrderItem;
import com.suresell.orders.domain.model.OrderDeliveryTracking;
import com.suresell.orders.domain.model.OrderStatus;
import com.suresell.orders.infrastructure.persistence.MenuCategoryRepository;
import com.suresell.orders.infrastructure.persistence.MenuProductRepository;
import com.suresell.orders.infrastructure.persistence.OrderRepository;
import com.suresell.orders.infrastructure.persistence.OrderDeliveryTrackingRepository;
import com.suresell.orders.infrastructure.persistence.OrderItemRepository;
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
    private final OrderItemRepository orderItemRepository;

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
    public void syncOrdersFromCloud() {
        if (cloudJdbcTemplate.isEmpty()) return;
        try {
            JdbcTemplate cloud = cloudJdbcTemplate.get();

            // 1. Traer órdenes de la nube de las últimas 24 horas
            String sqlOrders = "SELECT * FROM orders WHERE created_at > NOW() - INTERVAL '24 hours' ORDER BY created_at DESC";

            cloud.query(sqlOrders, (rs) -> {
                UUID uuid = UUID.fromString(rs.getString("uuid_id"));
                
                if (!orderRepository.existsById(uuid)) {
                    log.info("Detectada orden externa nueva: {}. Importando...", uuid);
                    
                    Order newOrder = new Order();
                    newOrder.setUuidId(uuid);
                    newOrder.setIdOrder(rs.getLong("id_order"));
                    newOrder.setPagerColor(rs.getString("pager_color"));
                    newOrder.setPagerNumber(rs.getString("pager_number"));
                    newOrder.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    newOrder.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    newOrder.setPaymentMethod(rs.getString("payment_method"));
                    newOrder.setSubtotal(rs.getBigDecimal("subtotal"));
                    newOrder.setTotal(rs.getBigDecimal("total"));
                    newOrder.setDiscountCode(rs.getString("discount_code"));
                    newOrder.setDiscountPercentage(rs.getBigDecimal("discount_percentage"));
                    newOrder.setDiscountAmount(rs.getBigDecimal("discount_amount"));
                    newOrder.setIsPrinted(rs.getBoolean("is_printed"));
                    newOrder.setSynced(true);
                    newOrder.setNew(true);

                    // Guardado inicial para que exista en el contexto de persistencia
                    Order savedOrder = orderRepository.save(newOrder);

                    // 2. Traer sus Items y Tracking usando el UUID como objeto
                    importOrderItems(savedOrder, cloud);
                    importOrderTracking(savedOrder, cloud);
                    
                    log.info("Orden externa {} y sus componentes importados.", uuid);
                }
            });
        } catch (Exception e) {
            log.error("Error sincronizando órdenes desde la nube: {}", e.getMessage());
        }
    }

    private void importOrderItems(Order order, JdbcTemplate cloud) {
        String sqlItems = "SELECT * FROM order_item WHERE order_uuid_id = ?";
        cloud.query(sqlItems, (rs) -> {
            OrderItem item = new OrderItem();
            item.setUuidId(UUID.fromString(rs.getString("uuid_id")));
            item.setOrderId(rs.getLong("order_id"));
            item.setProductId(rs.getString("product_id"));
            item.setQuantity(rs.getInt("quantity"));
            item.setUnitPrice(rs.getBigDecimal("unit_price"));
            item.setTotalPrice(rs.getBigDecimal("total_price"));
            item.setInstructions(rs.getString("instructions"));
            item.setComboGroup(rs.getInt("combo_group"));
            
            item.setOrder(order);
            item.setNew(true);

            orderItemRepository.save(item);
            log.info("Item {} importado para orden {}", item.getUuidId(), order.getUuidId());
        }, order.getUuidId());
    }

    private void importOrderTracking(Order order, JdbcTemplate cloud) {
        String sqlTracking = "SELECT * FROM order_delivery_tracking WHERE order_id_uuid = ?";
        cloud.query(sqlTracking, (rs) -> {
            OrderDeliveryTracking dt = new OrderDeliveryTracking();
            dt.setOrderIdUuid(order.getUuidId());
            dt.setOrderId(rs.getLong("order_id"));
            dt.setDelivered(rs.getBoolean("delivered"));
            dt.setPagerReturned(rs.getBoolean("pager_returned"));
            dt.setPreparationDurationSeconds(rs.getInt("preparation_duration_seconds"));
            
            dt.setOrder(order);
            dt.setNew(true);

            orderDeliveryTrackingRepository.save(dt);
            log.info("Tracking importado para orden {}", order.getUuidId());
        }, order.getUuidId());
    }

    @Transactional
    public void syncActiveOrdersTrackingFromCloud() {
        if (cloudJdbcTemplate.isEmpty()) return;
        try {
            List<Order> activeOrders = orderRepository.findActiveOrdersWithItems(OrderStatus.pagado, false);
            if (activeOrders.isEmpty()) return;

            List<UUID> orderUuids = activeOrders.stream().map(Order::getUuidId).toList();
            JdbcTemplate cloud = cloudJdbcTemplate.get();
            String inSql = orderUuids.stream().map(uuid -> "'" + uuid + "'").collect(Collectors.joining(","));
            String sql = "SELECT order_id_uuid, delivered, pager_returned, preparation_duration_seconds FROM order_delivery_tracking WHERE order_id_uuid IN (" + inSql + ") AND (delivered = true OR pager_returned = true)";
            
            cloud.query(sql, (rs) -> {
                UUID orderUuid = UUID.fromString(rs.getString("order_id_uuid"));
                boolean delivered = rs.getBoolean("delivered");
                boolean pagerReturned = rs.getBoolean("pager_returned");
                int duration = rs.getInt("preparation_duration_seconds");

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
