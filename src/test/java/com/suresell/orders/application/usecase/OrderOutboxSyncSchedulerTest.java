package com.suresell.orders.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suresell.orders.domain.model.SyncOutbox;
import com.suresell.orders.domain.port.out.OrderCloudSyncPort;
import com.suresell.orders.domain.port.out.SyncOutboxRepositoryPort;
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
    private SyncOutboxRepositoryPort syncOutboxRepositoryPort;

    @Mock
    private OrderCloudSyncPort orderCloudSyncPort;

    private SyncOutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SyncOutboxScheduler(syncOutboxRepositoryPort, orderCloudSyncPort);
        ReflectionTestUtils.setField(scheduler, "cloudSyncEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 20);
    }

    @Test
    void syncPendingDataMarksSyncedWhenCloudSyncSucceeds() {
        SyncOutbox outbox = new SyncOutbox();
        outbox.setId(10L);
        outbox.setAggregateId(501L);
        outbox.setPayloadJson("{\"ok\":true}");
        outbox.setAttempts(0);
        outbox.setAggregateType("ORDER");

        when(syncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20))).thenReturn(List.of(outbox));
        when(syncOutboxRepositoryPort.markInProgress(eq(10L), any(Long.class))).thenReturn(true);

        scheduler.syncPendingData();

        verify(orderCloudSyncPort).syncOrderCreatedPayload("{\"ok\":true}");
        verify(syncOutboxRepositoryPort).markSynced(eq(10L), any(Long.class), any(Long.class));
        verify(syncOutboxRepositoryPort, never())
                .markFailed(any(Long.class), any(String.class), any(Integer.class), any(Long.class), any(Long.class));
    }

    @Test
    void syncPendingDataMarksFailedWhenCloudSyncThrows() {
        SyncOutbox outbox = new SyncOutbox();
        outbox.setId(11L);
        outbox.setAggregateId(777L);
        outbox.setPayloadJson("{\"ok\":false}");
        outbox.setAttempts(0);

        when(syncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20))).thenReturn(List.of(outbox));
        when(syncOutboxRepositoryPort.markInProgress(eq(11L), any(Long.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("Cloud down"))
                .when(orderCloudSyncPort).syncOrderCreatedPayload("{\"ok\":false}");

        scheduler.syncPendingData();

        verify(syncOutboxRepositoryPort).markFailed(
                eq(11L),
                any(String.class),
                eq(1),
                any(Long.class),
                any(Long.class));
    }

    @Test
    void syncPendingDataContinuesProcessingNextRecordsWhenFirstFails() {
        SyncOutbox first = new SyncOutbox();
        first.setId(20L);
        first.setAggregateId(100L);
        first.setPayloadJson("{\"id\":1}");
        first.setAttempts(0);
        first.setAggregateType("ORDER");

        SyncOutbox second = new SyncOutbox();
        second.setId(21L);
        second.setAggregateId(101L);
        second.setPayloadJson("{\"id\":2}");
        second.setAttempts(0);
        second.setAggregateType("ORDER");

        when(syncOutboxRepositoryPort.findReadyForSync(any(Long.class), eq(20)))
                .thenReturn(List.of(first, second));
        when(syncOutboxRepositoryPort.markInProgress(eq(20L), any(Long.class))).thenReturn(true);
        when(syncOutboxRepositoryPort.markInProgress(eq(21L), any(Long.class))).thenReturn(true);
        
        org.mockito.Mockito.doThrow(new RuntimeException("Cloud down"))
                .when(orderCloudSyncPort).syncOrderCreatedPayload("{\"id\":1}");

        scheduler.syncPendingData();

        verify(orderCloudSyncPort, times(1)).syncOrderCreatedPayload("{\"id\":1}");
        verify(orderCloudSyncPort, times(1)).syncOrderCreatedPayload("{\"id\":2}");
        verify(syncOutboxRepositoryPort, times(1)).markInProgress(eq(21L), any(Long.class));
    }
}
