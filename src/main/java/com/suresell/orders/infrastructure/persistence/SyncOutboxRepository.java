package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.SyncOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface SyncOutboxRepository extends JpaRepository<SyncOutbox, Long> {
    @Query("""
            SELECT o
            FROM SyncOutbox o
            WHERE o.status IN ('PENDING', 'FAILED')
              AND o.nextRetryAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<SyncOutbox> findReadyForSync(@Param("now") Long now, Pageable pageable);
    @Modifying
    @Query("""
            UPDATE SyncOutbox o
            SET o.status = 'IN_PROGRESS',
                o.updatedAt = :updatedAt
            WHERE o.id = :id
              AND o.status IN ('PENDING', 'FAILED')
            """)
    int markInProgress(@Param("id") Long id, @Param("updatedAt") Long updatedAt);
    @Modifying
    @Query("""
            UPDATE SyncOutbox o
            SET o.status = 'SYNCED',
                o.lastError = null,
                o.updatedAt = :updatedAt,
                o.syncedAt = :syncedAt
            WHERE o.id = :id
            """)
    int markSynced(@Param("id") Long id, @Param("updatedAt") Long updatedAt, @Param("syncedAt") Long syncedAt);
    @Modifying
    @Query("""
            UPDATE SyncOutbox o
            SET o.status = 'FAILED',
                o.lastError = :error,
                o.attempts = :attempts,
                o.nextRetryAt = :nextRetryAt,
                o.updatedAt = :updatedAt
            WHERE o.id = :id
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("attempts") Integer attempts,
            @Param("nextRetryAt") Long nextRetryAt,
            @Param("updatedAt") Long updatedAt);
}
