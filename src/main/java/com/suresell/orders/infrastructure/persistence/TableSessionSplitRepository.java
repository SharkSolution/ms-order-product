package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.TableSessionSplit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Auditoría de cuentas divididas (RLS acota al tenant). */
public interface TableSessionSplitRepository extends JpaRepository<TableSessionSplit, Long> {

    /**
     * Ajuste por redondeo asumido por el negocio en la ventana del cierre,
     * calculado AL VUELO.
     *
     * <p>No hay un total guardado en {@code daily_closures} que pudiera quedar
     * desincronizado: el cierre suma de acá cada vez. Es determinista —una
     * división ya cobrada no cambia— así que reabrir un cierre viejo da lo mismo.
     *
     * <p>{@code COALESCE} porque un turno sin mesas divididas daría nulo y el
     * cierre no puede fallar por eso.
     */
    @Query("""
            SELECT COALESCE(SUM(s.ajusteRedondeo), 0)
            FROM TableSessionSplit s
            WHERE s.createdAt BETWEEN :desde AND :hasta
            """)
    BigDecimal sumaAjustePorRedondeo(@Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);

    /** El detalle de las divisiones del turno, para explicar el ajuste del cierre. */
    @Query("""
            SELECT s FROM TableSessionSplit s
            WHERE s.createdAt BETWEEN :desde AND :hasta
            ORDER BY s.createdAt
            """)
    List<TableSessionSplit> entre(@Param("desde") LocalDateTime desde,
                                  @Param("hasta") LocalDateTime hasta);

    List<TableSessionSplit> findByTableSessionId(UUID tableSessionId);
}
