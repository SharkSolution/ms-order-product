package com.suresell.order.mapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.suresell.order.model.entity.Order;
import com.suresell.order.model.entity.OrderItem;
import com.suresell.order.model.record.OrderItemResponseRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import com.suresell.order.rest_client.ProductClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class OrderMapper {

    private final ProductClient productClient;

    // Cache cross-request con Caffeine - persiste entre requests
    // TTL: 15 minutos, máximo 500 productos en cache
    private final Cache<String, String> productNameCache;

    public OrderMapper(ProductClient productClient) {
        this.productClient = productClient;
        this.productNameCache = Caffeine.newBuilder()
                .maximumSize(500)                    // Máximo 500 productos en cache
                .expireAfterWrite(Duration.ofMinutes(15))  // TTL 15 minutos
                .recordStats()                       // Para métricas (opcional)
                .build();
        log.info("ProductNameCache inicializado - maxSize=500, TTL=15min");
    }

    public OrderResponseRecord toOrderResponse(Order order) {
        List<OrderItemResponseRecord> items = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .toList();

        return new OrderResponseRecord(
                order.getIdOrder(),
                order.getPagerColor(),
                order.getPagerNumber(),
                order.getCreatedAt(),
                order.getSubtotal(),
                order.getTotal(),
                order.getStatus().getDisplayName(),
                order.getPaymentMethod(),
                order.getDiscountCode(),
                order.getDiscountPercentage(),
                order.getDiscountAmount(),
                order.getDeliveredAt(),
                order.getElapsedSecondsToDeliver(),
                items
        );
    }

    private OrderItemResponseRecord toOrderItemResponse(OrderItem item) {
        // Usar cache Caffeine - persiste entre requests (mucho mejor que ThreadLocal)
        String productName = productNameCache.get(
                item.getProductId(),
                productClient::getProductName
        );

        return new OrderItemResponseRecord(
                item.getProductId(),
                productName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getInstructions(),
                item.getComboGroup()
        );
    }

    /**
     * Ya no es necesario limpiar manualmente - Caffeine maneja TTL y eviction.
     * Mantener método por compatibilidad, pero ahora es no-op.
     */
    public void clearProductNameCache() {
        // No-op: Caffeine maneja la eviction automáticamente
        // Si se necesita forzar, descomentar: productNameCache.invalidateAll();
    }

    /**
     * Retorna estadísticas del cache (útil para monitoring).
     */
    public String getCacheStats() {
        var stats = productNameCache.stats();
        return String.format("Cache stats - hits: %d, misses: %d, hitRate: %.2f%%, size: %d",
                stats.hitCount(), stats.missCount(), stats.hitRate() * 100,
                productNameCache.estimatedSize());
    }

    /**
     * Obtiene nombre de producto usando cache Caffeine.
     * Reutilizable desde otros servicios (ej: ResilientOrderService).
     *
     * @param productId ID del producto
     * @return Nombre del producto o "Producto no disponible" si falla
     */
    public String getProductNameCached(String productId) {
        return productNameCache.get(
            productId,
            productClient::getProductName
        );
    }
}
