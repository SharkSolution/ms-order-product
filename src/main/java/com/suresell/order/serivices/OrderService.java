/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.OrderRequestRecord
 *  com.suresell.order.model.record.OrderResponseRecord
 *  com.suresell.order.serivices.OrderService
 */
package com.suresell.order.serivices;
import com.suresell.order.model.record.OrderRequestRecord;
import com.suresell.order.model.record.OrderResponseRecord;
import java.util.List;
public interface OrderService {
    public void createOrUpdateOrder(OrderRequestRecord var1);
    public List<OrderResponseRecord> getKitchenOrders();
    public List<OrderResponseRecord> getAllOrders();
    public OrderResponseRecord getOrderById(Long var1);
    public void updateStatus(Long var1, String var2);
    public void updateOrder(Long var1, OrderRequestRecord var2);
    public List<OrderResponseRecord> getSalesReport();
    public void updatePaymentMethod(Long var1, String var2);
    public OrderResponseRecord applyDiscountToOrder(Long var1, String var2);
    public void markAsDelivered(Long var1, Integer var2);
}
