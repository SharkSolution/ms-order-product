package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.TableSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Cuentas de mesa (RLS acota al tenant de la sesión). */
public interface TableSessionRepository extends JpaRepository<TableSession, UUID> {

    /** Sesiones vivas (ABIERTA o COBRANDO). Es lo que bloquea el cierre de caja. */
    @Query("SELECT s FROM TableSession s WHERE s.status <> 'CERRADA' ORDER BY s.openedAt")
    List<TableSession> findVivas();

    @Query("SELECT s FROM TableSession s WHERE s.tableId = :tableId AND s.status <> 'CERRADA'")
    Optional<TableSession> findVivaPorMesa(Long tableId);

    long countByStatusNot(String status);
}
