package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.SyncOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El outbox solo debe persistir cuando el sync a la nube está activo. En el perfil
 * cloud (sync.cloud.enabled=false) ningún scheduler lo drena, así que encolar solo
 * dejaría filas inertes → el save es no-op. Ver docs/100 §5.
 */
class SyncOutboxRepositoryAdapterTest {

    @Test
    void doesNotPersistWhenCloudSyncDisabled() {
        SyncOutboxRepository repo = mock(SyncOutboxRepository.class);
        SyncOutboxRepositoryAdapter adapter =
                new SyncOutboxRepositoryAdapter(repo, mock(OrderRepository.class));
        ReflectionTestUtils.setField(adapter, "cloudSyncEnabled", false);

        SyncOutbox event = new SyncOutbox();
        SyncOutbox returned = adapter.save(event);

        assertSame(event, returned, "devuelve el evento sin persistir");
        verifyNoInteractions(repo);
    }

    @Test
    void persistsWhenCloudSyncEnabled() {
        SyncOutboxRepository repo = mock(SyncOutboxRepository.class);
        SyncOutbox event = new SyncOutbox();
        when(repo.save(event)).thenReturn(event);
        SyncOutboxRepositoryAdapter adapter =
                new SyncOutboxRepositoryAdapter(repo, mock(OrderRepository.class));
        ReflectionTestUtils.setField(adapter, "cloudSyncEnabled", true);

        adapter.save(event);

        verify(repo).save(event);
    }
}
