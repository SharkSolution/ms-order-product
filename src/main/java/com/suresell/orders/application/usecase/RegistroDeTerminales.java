package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.Terminal;
import com.suresell.orders.infrastructure.persistence.TerminalRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da de alta terminales que aparecen por primera vez, y registra su contacto.
 *
 * <h3>El servidor NUNCA rechaza un terminal desconocido</h3>
 *
 * Es la decisión de diseño de V35 y conviene entender por qué: el POS genera su
 * UUID en el primer arranque y puede pasar días vendiendo sin conexión antes de
 * sincronizar. Si el servidor exigiera un registro previo, un local sin internet
 * no podría abrir caja — y perder ventas para proteger un registro es un mal
 * negocio.
 *
 * <p>Así que el terminal desconocido se da de alta con lo que se sabe de él (su
 * UUID y el negocio del JWT) y el administrador le pone nombre después.
 *
 * <h3>Por qué esto no puede tumbar una venta</h3>
 *
 * Corre en una transacción PROPIA ({@code REQUIRES_NEW}) y **se traga sus
 * errores a propósito**, que es la única excepción deliberada al criterio de
 * `FALLBACK-SILENCIOSO.md`: el registro del terminal es metadato, la venta es el
 * hecho. Si el alta falla, la orden se guarda igual con su `terminal_id` — la
 * clave foránea es nullable justo para esto — y el terminal se registrará en la
 * siguiente sincronización.
 *
 * <p>La degradación no es invisible: queda el WARN y, sobre todo, queda la
 * propia orden con un `terminal_id` que no está en `terminals`. Esa
 * inconsistencia es detectable con una consulta, cosa que un `null` no sería.
 */
@Log4j2
@Service
public class RegistroDeTerminales {

    private final TerminalRepository repositorio;

    public RegistroDeTerminales(TerminalRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Asegura que el terminal existe y anota su contacto.
     *
     * @param id    UUID que generó el cliente. Si es null no hace nada: un
     *              cliente viejo que no manda terminal sigue vendiendo
     * @param epoch epoch que declara el cliente; 1 si no lo manda
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asegurarRegistrado(UUID id, Integer epoch) {
        if (id == null) {
            return;
        }
        int epochSeguro = (epoch == null || epoch < 1) ? 1 : epoch;
        try {
            if (repositorio.registrarContacto(id, epochSeguro, OffsetDateTime.now()) == 0) {
                darDeAlta(id, epochSeguro);
            }
        } catch (DataIntegrityViolationException e) {
            // Dos sincronizaciones del mismo terminal a la vez: la otra ganó la
            // carrera y ya lo insertó. No es un error.
            log.debug("Terminal {} dado de alta por otra peticion concurrente", id);
        } catch (Exception e) {
            // Ver el javadoc: el registro es metadato, la venta es el hecho.
            log.warn("No se pudo registrar el terminal {} ({}). La venta sigue adelante; "
                    + "el terminal quedara registrado en la proxima sincronizacion.",
                    id, e.getClass().getSimpleName());
        }
    }

    private void darDeAlta(UUID id, int epoch) {
        Terminal nuevo = new Terminal();
        nuevo.setId(id);
        // `tenant_id` lo pone TenantEntityListener desde el negocio de la sesion;
        // `site_id`, `codigo` y `alias` quedan nulos: el servidor no puede saber
        // como llama el negocio a esta caja y no se lo inventa.
        nuevo.setEstado(Terminal.ACTIVO);
        nuevo.setRegistradoEn(OffsetDateTime.now());
        nuevo.setUltimaConexionEn(OffsetDateTime.now());
        nuevo.setEpochVisto(epoch);
        repositorio.save(nuevo);
        log.info("Terminal {} dado de alta automaticamente (epoch {})", id, epoch);
    }
}
