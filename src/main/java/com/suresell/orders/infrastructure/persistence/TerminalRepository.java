package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.Terminal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TerminalRepository extends JpaRepository<Terminal, UUID> {

    /**
     * Sube {@code epoch_visto} y refresca el último contacto, sin pisar nada más.
     *
     * <p>Es un UPDATE dirigido y no un {@code save()} de la entidad a propósito:
     * el alias y el código los edita el administrador desde el panel, y un
     * terminal que sincroniza no puede pisarlos con lo que él cree recordar.
     *
     * <p>{@code GREATEST} y no asignación directa: si llegan fuera de orden dos
     * sincronizaciones con epochs distintos, el epoch visto nunca retrocede.
     */
    @Modifying
    @Query("""
            UPDATE Terminal t
               SET t.epochVisto = CASE WHEN :epoch > t.epochVisto THEN :epoch ELSE t.epochVisto END,
                   t.ultimaConexionEn = :ahora
             WHERE t.id = :id
            """)
    int registrarContacto(@Param("id") UUID id,
                          @Param("epoch") Integer epoch,
                          @Param("ahora") java.time.OffsetDateTime ahora);
}
