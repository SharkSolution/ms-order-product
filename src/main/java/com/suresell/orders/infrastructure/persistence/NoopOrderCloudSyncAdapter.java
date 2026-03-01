package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnMissingBean(OrderCloudSyncPort.class)
public class NoopOrderCloudSyncAdapter implements OrderCloudSyncPort {
    @Override
    public void syncOrderCreatedPayload(String payloadJson) {
    }
}
