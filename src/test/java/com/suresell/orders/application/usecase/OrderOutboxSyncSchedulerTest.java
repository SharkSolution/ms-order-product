package com.suresell.orders.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suresell.orders.domain.model.OrderSyncOutbox;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import com.suresell.orders.domain.port.out.OrderSyncOutboxRepositoryPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderOutboxSyncSchedulerTest {

    @Mock
    private OrderSyncOutboxRepositoryPort orderSyncOutboxRepositoryPort;

    @Mock
    private OrderCloudSyncPort orderCloudSyncPort;

    private OrderOutboxSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderOutboxSyncScheduler(orderSyncOutboxRepositoryPort, orderCloudSyncPort);
        ReflectionTestUtils.setField(scheduler, "cloudSyncEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 20);
    }

    @Test
    void syncPendingOrdersMarksSyncedWhenCloudSyncSucceeds() {
        OrderSyncOutbox outbox = new OrderSyncOutbox();
        outbox.setId(10L);
        outbox.setAggregateId(501L);
        outbox.setPayloadJson("{\"ok\":true}");
        outbox.setAttempts(0);

        when(orderSyncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20))).thenReturn(List.of(outbox));
        when(orderSyncOutboxRepositoryPort.markInProgress(eq(10L), any(Long.class))).thenReturn(true);

        scheduler.syncPendingOrders();

        verify(orderCloudSyncPort).syncOrderCreatedPayload("{\"ok\":true}");
        verify(orderSyncOutboxRepositoryPort).markSynced(eq(10L), any(Long.class), any(Long.class));
        verify(orderSyncOutboxRepositoryPort, never())
                .markFailed(any(Long.class), any(String.class), any(Integer.class), any(Long.class), any(Long.class));
    }

    @Test
    void syncPendingOrdersMarksFailedWhenCloudSyncThrows() {
        OrderSyncOutbox outbox = new OrderSyncOutbox();
        outbox.setId(11L);
        outbox.setAggregateId(777L);
        outbox.setPayloadJson("{\"ok\":false}");
        outbox.setAttempts(0);

        when(orderSyncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20))).thenReturn(List.of(outbox));
        when(orderSyncOutboxRepositoryPort.markInProgress(eq(11L), any(Long.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Cloud down"))
                .when(orderCloudSyncPort).syncOrderCreatedPayload("{\"ok\":false}");

        scheduler.syncPendingOrders();

        verify(orderSyncOutboxRepositoryPort).markFailed(
                eq(11L),
                any(String.class),
                eq(1),
                any(Long.class),
                any(Long.class));
    }

    @Test
    void syncPendingOrdersStopsProcessingNextRecordsWhenFirstFails() {
        OrderSyncOutbox first = new OrderSyncOutbox();
        first.setId(20L);
        first.setAggregateId(100L);
        first.setPayloadJson("{\"id\":1}");
        first.setAttempts(0);

        OrderSyncOutbox second = new OrderSyncOutbox();
        second.setId(21L);
        second.setAggregateId(101L);
        second.setPayloadJson("{\"id\":2}");
        second.setAttempts(0);

        when(orderSyncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20)))
                .thenReturn(List.of(first, second));
        when(orderSyncOutboxRepositoryPort.markInProgress(eq(20L), any(Long.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Cloud down"))
                .when(orderCloudSyncPort).syncOrderCreatedPayload("{\"id\":1}");

        scheduler.syncPendingOrders();

        verify(orderCloudSyncPort, times(1)).syncOrderCreatedPayload("{\"id\":1}");
        verify(orderCloudSyncPort, never()).syncOrderCreatedPayload("{\"id\":2}");
        verify(orderSyncOutboxRepositoryPort, never()).markInProgress(eq(21L), any(Long.class));
    }
}
