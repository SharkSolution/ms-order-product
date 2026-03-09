package com.suresell.orders.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
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
                String eventType = root.path("eventType").asText();
                switch (eventType) {
                    case "ORDER_CREATED":
                        syncOrder(root);
                        break;
                    case "ORDER_PRINTED_UPDATED":
                        updateOrderPrintedInCloud(root);
                        break;
                    case "CLOSURE_CREATED":
                        upsertClosure(root.path("closure"));
                        break;
                    case "COUPON_SAVED":
                        upsertCoupon(root.path("coupon"));
                        break;
                    case "COUPON_PRODUCT_SAVED":
                        upsertCouponProduct(root);
                        break;
                    case "TRACKING_UPDATED":
                        upsertDeliveryTracking(root.path("tracking"), asLong(root.path("orderId")));
                        break;
                    case "DISCOUNT_USAGE_CREATED":
                        upsertDiscountUsage(root.path("usage"));
                        break;
                    case "EDIT_HISTORY_CREATED":
                        upsertEditHistory(root.path("history"));
                        break;
                    default:
                        log.warn("Evento de sincronización no reconocido: {}", eventType);
                }
            } catch (Exception ex) {
                log.error("Error detallado de sincronización cloud: ", ex);
                throw new IllegalStateException("Error sincronizando a cloud: " + ex.getMessage(), ex);
            }
        });
    }

    private void syncOrder(JsonNode root) {
        JsonNode orderNode = root.path("order");
        JsonNode trackingNode = root.path("tracking");
        Long orderId = asLong(orderNode.path("idOrder"));
        upsertOrder(orderNode, orderId);
        upsertDeliveryTracking(trackingNode, orderId);
        upsertOrderItems(orderNode.path("items"), orderId);
    }

    private void updateOrderPrintedInCloud(JsonNode root) {
        Long orderId = asLong(root.path("idOrder"));
        Boolean isPrinted = asBoolean(root.path("isPrinted"));

        cloudJdbcTemplate.update(
                "UPDATE orders SET is_printed = ?, synced = true WHERE id_order = ?",
                isPrinted, orderId);

        log.info("Bandera is_printed de orden #{} actualizada a {} en cloud.", orderId, isPrinted);
    }

    private void upsertOrder(JsonNode orderNode, Long orderId) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO orders (
                    id_order, created_at, discount_amount, discount_code, discount_percentage,
                    pager_color, pager_number, payment_method, status, subtotal, total, synced, is_printed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id_order) DO UPDATE SET
                    status = EXCLUDED.status,
                    subtotal = EXCLUDED.subtotal,
                    total = EXCLUDED.total,
                    pager_color = EXCLUDED.pager_color,
                    pager_number = EXCLUDED.pager_number,
                    discount_amount = EXCLUDED.discount_amount,
                    discount_code = EXCLUDED.discount_code,
                    discount_percentage = EXCLUDED.discount_percentage,
                    payment_method = EXCLUDED.payment_method,
                    is_printed = EXCLUDED.is_printed,
                    synced = true
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
                asBigDecimal(orderNode.path("total")),
                true,
                asBoolean(orderNode.path("isPrinted")));
    }

    private void upsertDeliveryTracking(JsonNode trackingNode, Long orderId) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO order_delivery_tracking (
                    order_id, delivered, pager_returned, preparation_duration_seconds
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    delivered = EXCLUDED.delivered,
                    pager_returned = EXCLUDED.pager_returned,
                    preparation_duration_seconds = EXCLUDED.preparation_duration_seconds
                """,
                orderId,
                asBoolean(trackingNode.path("delivered")),
                asBoolean(trackingNode.path("pagerReturned")),
                asInteger(trackingNode.path("preparationDurationSeconds")));
    }

    private void upsertOrderItems(JsonNode itemsNode, Long orderId) {
        cloudJdbcTemplate.update("DELETE FROM order_item WHERE order_id = ?", orderId);
        if (itemsNode == null || itemsNode.isMissingNode() || !itemsNode.isArray()) return;
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

    private void upsertClosure(JsonNode n) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO daily_closure (
                    id, user_name, opening_time, closing_time, total_expected_cash, total_expected_card,
                    total_expected_nequi, total_expected_qr, total_counted_cash, total_counted_card,
                    total_counted_nequi, total_counted_qr, difference_amount, status, notes, 
                    base_balance_for_next_day, cash_count_audit
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    notes = EXCLUDED.notes,
                    total_counted_cash = EXCLUDED.total_counted_cash,
                    total_counted_card = EXCLUDED.total_counted_card,
                    total_counted_nequi = EXCLUDED.total_counted_nequi,
                    total_counted_qr = EXCLUDED.total_counted_qr,
                    difference_amount = EXCLUDED.difference_amount,
                    closing_time = EXCLUDED.closing_time
                """,
                asString(n.path("id")), asString(n.path("userName")), asTimestamp(n.path("openingTime")),
                asTimestamp(n.path("closingTime")), asBigDecimal(n.path("totalExpectedCash")),
                asBigDecimal(n.path("totalExpectedCard")), asBigDecimal(n.path("totalExpectedNequi")),
                asBigDecimal(n.path("totalExpectedQr")), asBigDecimal(n.path("totalCountedCash")),
                asBigDecimal(n.path("totalCountedCard")), asBigDecimal(n.path("totalCountedNequi")),
                asBigDecimal(n.path("totalCountedQr")), asBigDecimal(n.path("differenceAmount")),
                asString(n.path("status")), asString(n.path("notes")),
                asBigDecimal(n.path("baseBalanceForNextDay")), asString(n.path("cashCountAudit")));
    }

    private void upsertCoupon(JsonNode n) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO discount_coupon (
                    id, code, name, description, discount_percentage, valid_from, valid_to, 
                    valid_weekdays, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    code = EXCLUDED.code,
                    name = EXCLUDED.name,
                    description = EXCLUDED.description,
                    discount_percentage = EXCLUDED.discount_percentage,
                    valid_from = EXCLUDED.valid_from,
                    valid_to = EXCLUDED.valid_to,
                    valid_weekdays = EXCLUDED.valid_weekdays,
                    is_active = EXCLUDED.is_active,
                    updated_at = EXCLUDED.updated_at
                """,
                asLong(n.path("id")), asString(n.path("code")), asString(n.path("name")),
                asString(n.path("description")), asBigDecimal(n.path("discountPercentage")),
                asLocalDate(n.path("validFrom")), asLocalDate(n.path("validTo")),
                asString(n.path("validWeekdays")), asBoolean(n.path("isActive")),
                asTimestamp(n.path("createdAt")), asTimestamp(n.path("updatedAt")));
    }

    private void upsertCouponProduct(JsonNode root) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO coupon_product (id, coupon_id, product_id, product_name)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    product_id = EXCLUDED.product_id,
                    product_name = EXCLUDED.product_name
                """,
                asLong(root.path("id")), asLong(root.path("couponId")),
                asString(root.path("productId")), asString(root.path("productName")));
    }

    private void upsertDiscountUsage(JsonNode n) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO discount_usage (
                    id, order_id, coupon_id, discount_code, subtotal_before_discount, 
                    discount_amount, total_after_discount, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                asLong(n.path("id")), asLong(n.path("orderId")), asLong(n.path("couponId")),
                asString(n.path("discountCode")), asBigDecimal(n.path("subtotalBeforeDiscount")),
                asBigDecimal(n.path("discountAmount")), asBigDecimal(n.path("totalAfterDiscount")),
                asTimestamp(n.path("createdAt")));
    }

    private void upsertEditHistory(JsonNode n) {
        cloudJdbcTemplate.update(
                """
                INSERT INTO order_edit_history (
                    id, order_id, edit_type, product_id, product_name, old_quantity, new_quantity,
                    old_total, new_total, edited_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                asLong(n.path("id")), asLong(n.path("orderId")), asString(n.path("editType")),
                asString(n.path("productId")), asString(n.path("productName")), asInteger(n.path("oldQuantity")),
                asInteger(n.path("newQuantity")), asBigDecimal(n.path("oldTotal")),
                asBigDecimal(n.path("newTotal")), asTimestamp(n.path("editedAt")));
    }

    private String asString(JsonNode node) { return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText(); }
    private Long asLong(JsonNode node) { return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asLong(); }
    private Integer asInteger(JsonNode node) { return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asInt(); }
    private Boolean asBoolean(JsonNode node) { return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asBoolean(); }

    private BigDecimal asBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String text = node.asText();
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return node.isNumber() ? node.decimalValue() : new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }

    private java.sql.Date asLocalDate(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try { return java.sql.Date.valueOf(node.asText()); } catch (Exception e) { return null; }
    }

    private Timestamp asTimestamp(JsonNode node) {
        LocalDateTime dateTime = asLocalDateTime(node);
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime asLocalDateTime(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isTextual()) {
            try { return LocalDateTime.parse(node.asText()); } catch (Exception e) { return null; }
        }
        if (node.isArray() && node.size() >= 6) {
            return LocalDateTime.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt(),
                                    node.get(3).asInt(), node.get(4).asInt(), node.get(5).asInt(),
                                    node.size() > 6 ? node.get(6).asInt() : 0);
        }
        return null;
    }
}
