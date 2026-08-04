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

    /**
     * Ajuste por redondeo asumido por el negocio en la ventana del cierre.
     *
     * <p>Se filtra por {@code closedAt} —cuándo se COBRÓ la mesa— y no por
     * cuándo se abrió: el ajuste nace en el cobro. Además el cierre se BLOQUEA
     * si queda alguna mesa sin cobrar, así que ninguna cuenta puede quedar a
     * caballo entre dos turnos.
     *
     * <p>{@code COALESCE} porque sin mesas divididas la suma sería nula y el
     * cierre no puede fallar por eso.
     */
    @Query("""
            SELECT COALESCE(SUM(s.roundingAdjustment), 0)
            FROM TableSession s
            WHERE s.closedAt BETWEEN :desde AND :hasta
            """)
    java.math.BigDecimal sumaAjustePorRedondeo(
            @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde,
            @org.springframework.data.repository.query.Param("hasta") java.time.LocalDateTime hasta);

    /**
     * N3/#1 — Resuelve la MESA de un lote de cuentas, en una sola consulta.
     * Lo usa cocina para titular la comanda; se hace por lote para no meter un
     * N+1 en la cola, que se refresca cada pocos segundos.
     * Devuelve filas [sessionId (UUID), number (Integer), label (String)].
     */
    @Query("""
            SELECT s.id, t.number, t.label
            FROM TableSession s
            JOIN RestaurantTable t ON t.id = s.tableId
            WHERE s.id IN :sessionIds
            """)
    List<Object[]> findMesaPorSesion(java.util.Collection<UUID> sessionIds);
}
