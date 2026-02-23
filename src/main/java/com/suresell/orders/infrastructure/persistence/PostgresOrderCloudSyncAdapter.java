package com.suresell.orders.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sync.cloud", name = "enabled", havingValue = "true")
public class PostgresOrderCloudSyncAdapter implements OrderCloudSyncPort {

    private final @Qualifier("cloudJdbcTemplate") JdbcTemplate cloudJdbcTemplate;
    private final @Qualifier("cloudTransactionTemplate") TransactionTemplate cloudTransactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void syncOrderCreatedPayload(String payloadJson) {
        cloudTransactionTemplate.executeWithoutResult(status -> {
            try {
                JsonNode root = objectMapper.readTree(payloadJson);
                JsonNode orderNode = root.path("order");
                JsonNode trackingNode = root.path("tracking");

                Long orderId = asLong(orderNode.path("idOrder"));
                upsertOrder(orderNode, orderId);
                upsertDeliveryTracking(trackingNode, orderId);
                upsertOrderItems(orderNode.path("items"), orderId);
            } catch (Exception ex) {
                throw new IllegalStateException("Error sincronizando orden a cloud", ex);
            }
        });
    }

    private void upsertOrder(JsonNode orderNode, Long orderId) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO orders (
                    id_order, created_at, discount_amount, discount_code, discount_percentage,
                    pager_color, pager_number, payment_method, status, subtotal, total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id_order) DO UPDATE SET
                    created_at = EXCLUDED.created_at,
                    discount_amount = EXCLUDED.discount_amount,
                    discount_code = EXCLUDED.discount_code,
                    discount_percentage = EXCLUDED.discount_percentage,
                    pager_color = EXCLUDED.pager_color,
                    pager_number = EXCLUDED.pager_number,
                    payment_method = EXCLUDED.payment_method,
                    status = EXCLUDED.status,
                    subtotal = EXCLUDED.subtotal,
                    total = EXCLUDED.total
                """,
                orderId,
                asTimestamp(orderNode.path("createdAt")),
                asBigDecimal(orderNode.path("discountAmount")),
                asString(orderNode.path("discountCode")),
                asBigDecimal(orderNode.path("discountPercentage")),
                asString(orderNode.path("pagerColor")),
                asString(orderNode.path("pagerNumber")),
                asString(orderNode.path("paymentMethod")),
                asString(orderNode.path("status")),
                asBigDecimal(orderNode.path("subtotal")),
                asBigDecimal(orderNode.path("total")));
    }

    private void upsertDeliveryTracking(JsonNode trackingNode, Long orderId) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO order_delivery_tracking (
                    order_id, delivered, preparation_duration_seconds
                ) VALUES (?, ?, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    delivered = EXCLUDED.delivered,
                    preparation_duration_seconds = EXCLUDED.preparation_duration_seconds
                """,
                orderId,
                asBoolean(trackingNode.path("delivered")),
                asInteger(trackingNode.path("preparationDurationSeconds")));
    }

    private void upsertOrderItems(JsonNode itemsNode, Long orderId) {
        cloudJdbcTemplate.update("DELETE FROM order_item WHERE order_id = ?", orderId);
        if (itemsNode == null || !itemsNode.isArray()) {
            return;
        }
        for (JsonNode itemNode : itemsNode) {
            cloudJdbcTemplate.update(
                    """
                    INSERT INTO order_item (
                        combo_group, instructions, product_id, quantity,
                        total_price, unit_price, order_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    asInteger(itemNode.path("comboGroup")),
                    asString(itemNode.path("instructions")),
                    asString(itemNode.path("productId")),
                    asInteger(itemNode.path("quantity")),
                    asBigDecimal(itemNode.path("totalPrice")),
                    asBigDecimal(itemNode.path("unitPrice")),
                    orderId);
        }
    }

    private String asString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Long asLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private Boolean asBoolean(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private BigDecimal asBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return new BigDecimal(node.asText());
    }

    private Timestamp asTimestamp(JsonNode node) {
        LocalDateTime dateTime = asLocalDateTime(node);
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime asLocalDateTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return LocalDateTime.parse(node.asText());
        }
        if (node.isArray() && node.size() >= 6) {
            int year = node.get(0).asInt();
            int month = node.get(1).asInt();
            int day = node.get(2).asInt();
            int hour = node.get(3).asInt();
            int minute = node.get(4).asInt();
            int second = node.get(5).asInt();
            int nano = node.size() > 6 ? node.get(6).asInt() : 0;
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        }
        return null;
    }
}
