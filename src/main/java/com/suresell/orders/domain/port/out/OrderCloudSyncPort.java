package com.suresell.orders.domain.port.out;

public interface OrderCloudSyncPort {
    void syncOrderCreatedPayload(String payloadJson);
}
