package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.RestaurantTable;
import com.suresell.orders.domain.model.TableSession;
import com.suresell.orders.infrastructure.persistence.RestaurantTableRepository;
import com.suresell.orders.infrastructure.persistence.TableSessionRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Cuentas de mesa (Inc. 3 y 4 del modo Restaurante).
 *
 * El cobro es de la SESIÓN, no de cada orden: al cerrar la mesa todas sus
 * órdenes pasan a `pagado` de una, así el cierre de caja sigue funcionando sin
 * cambiar su lógica.
 */
@Service
@RequiredArgsConstructor
public class TableSessionService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final TableSessionRepository sessionRepository;
    private final RestaurantTableRepository tableRepository;

    public List<TableSession> vivas() {
        return sessionRepository.findVivas();
    }

    /**
     * Abre la cuenta de una mesa.
     *
     * La unicidad la garantiza el ÍNDICE ÚNICO PARCIAL de V25, no este código:
     * si dos cajas abren la misma mesa a la vez, la segunda choca contra la BD
     * y aquí se traduce a un mensaje claro. Un chequeo previo sería
     * check-then-act y las dos pasarían.
     */
    @Transactional
    public TableSession abrir(Integer numeroMesa, String usuario) {
        RestaurantTable mesa = tableRepository.findByNumber(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("No existe la mesa " + numeroMesa));
        if (!Boolean.TRUE.equals(mesa.getActive())) {
            throw new IllegalArgumentException("La mesa " + numeroMesa + " está inactiva");
        }
        TableSession sesion = new TableSession();
        sesion.setId(UUID.randomUUID());
        sesion.setTableId(mesa.getId());
        sesion.setSiteId(mesa.getSiteId());
        sesion.setStatus(TableSession.ABIERTA);
        sesion.setOpenedAt(LocalDateTime.now(BOGOTA));
        sesion.setOpenedBy(usuario);
        try {
            return sessionRepository.save(sesion);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "La mesa " + numeroMesa + " ya tiene una cuenta abierta");
        }
    }

    /** Marca quién está cobrando. Lock suave: avisa, no impide. */
    @Transactional
    public TableSession reclamarParaCobro(UUID sesionId, String usuario) {
        TableSession sesion = obtenerViva(sesionId);
        sesion.setStatus(TableSession.COBRANDO);
        sesion.setClaimedBy(usuario);
        sesion.setClaimedAt(LocalDateTime.now(BOGOTA));
        return sessionRepository.save(sesion);
    }

    @Transactional
    public TableSession cerrar(UUID sesionId) {
        TableSession sesion = obtenerViva(sesionId);
        sesion.setStatus(TableSession.CERRADA);
        sesion.setClosedAt(LocalDateTime.now(BOGOTA));
        return sessionRepository.save(sesion);
    }

    private TableSession obtenerViva(UUID sesionId) {
        TableSession sesion = sessionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la cuenta de mesa"));
        if (!sesion.estaViva()) {
            throw new IllegalStateException("Esa cuenta de mesa ya está cerrada");
        }
        return sesion;
    }

    /**
     * Cuántas mesas quedan sin cobrar. Lo usa el cierre de caja para BLOQUEARSE:
     * cerrar caja con mesas abiertas dejaría consumo sin cobrar fuera del cuadre.
     */
    public List<TableSession> pendientesDeCobro() {
        return sessionRepository.findVivas();
    }
}
