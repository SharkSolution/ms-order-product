package com.suresell.orders.multitenant;

import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * El actor con el que firman los procesos automáticos.
 *
 * <h3>Por qué existe</h3>
 *
 * La regla 4 de LINEAMIENTOS es explícita: <i>"Usuario responsable en cada
 * evento. <b>Sin excepción, ni en procesos automáticos.</b>"</i>
 *
 * <p>Los tres schedulers del servicio no dejaban autoría de ningún tipo. Sin un
 * actor sistema habría que aceptar filas sin autor "porque las hizo la máquina",
 * y esa excepción es justo la que convierte un campo obligatorio en uno que a
 * veces está vacío.
 *
 * <h3>Uno por negocio, no uno global</h3>
 *
 * {@code users.tenant_id} es NOT NULL con FK a {@code tenants}, así que un actor
 * global tendría que colgar de algún negocio — y un {@code created_by}
 * apuntando a la fila de otro negocio rompería el aislamiento que V1 y V33
 * construyen.
 *
 * <h3>No puede entrar</h3>
 *
 * <ul>
 *   <li>{@code role = 'sistema'}: ningún flujo de login concede ese rol.</li>
 *   <li>{@code status = 'disabled'}: {@code AuthService} lo rechaza.</li>
 *   <li>{@code password_hash = '!'}: no es un BCrypt válido —siempre empiezan
 *       por {@code $2}— así que ninguna contraseña puede coincidir. No es un
 *       secreto y no hay nada que rotar.</li>
 * </ul>
 *
 * <h3>Alta bajo demanda</h3>
 *
 * V37 lo crea para los negocios que ya existían. Este componente lo crea para
 * los que se den de alta después, la primera vez que un proceso automático
 * necesite firmar. Es el mismo criterio que {@code RegistroDeTerminales}: no se
 * rechaza la operación por falta de un registro que se puede crear al vuelo.
 */
@Log4j2
@Component
@Profile("cloud")
public class UsuarioDeSistema {

    /** Dominio reservado por la RFC 2606: nunca resuelve y nadie puede registrarlo. */
    private static final String DOMINIO = ".invalid";

    /** Ver el javadoc: imposible como hash BCrypt. */
    private static final String CLAVE_IMPOSIBLE = "!";

    public static final String ROL = "sistema";

    private final JdbcTemplate jdbc;

    public UsuarioDeSistema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Email del actor sistema de un negocio. Determinista, sin estado. */
    public static String emailDe(String tenantId) {
        return "sistema@" + tenantId + DOMINIO;
    }

    /**
     * Id del actor sistema del negocio, creándolo si hace falta.
     *
     * @return vacío si no se pudo resolver ni crear. El llamador registra la
     *         operación igual con {@code created_by} nulo: perder una venta por
     *         no poder firmarla sería el peor intercambio posible
     */
    public Optional<Long> idDe(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<Long> existente = buscar(tenantId);
            if (existente.isPresent()) {
                return existente;
            }
            jdbc.update("""
                    INSERT INTO users (email, password_hash, tenant_id, role, status)
                    VALUES (?, ?, ?, ?, 'disabled')
                    ON CONFLICT (email) DO NOTHING
                    """, emailDe(tenantId), CLAVE_IMPOSIBLE, tenantId, ROL);
            return buscar(tenantId);
        } catch (Exception e) {
            log.warn("No se pudo resolver el usuario de sistema de {} ({}). La operacion "
                    + "sigue adelante sin firma.", tenantId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<Long> buscar(String tenantId) {
        return jdbc.query("SELECT id FROM users WHERE email = ?",
                        rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.<Long>empty(),
                        emailDe(tenantId));
    }
}
