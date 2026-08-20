package com.suresell.orders.multitenant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limit anti-abuso de los endpoints de autenticación. En memoria — basta
 * para el backend single-instance de F1; si se escala horizontalmente hay que
 * mover esto a un store compartido (Redis). Ver docs/110 §8.
 *
 * <h3>Tres cupos independientes</h3>
 *
 * Cada endpoint lleva su propia cuenta ({@link Bucket}), porque el abuso que
 * hay que frenar es distinto en cada uno y mezclarlos haría que un intento de
 * registro consumiera el cupo de un login:
 *
 * <ul>
 *   <li>{@link Bucket#REGISTRO} — {@value #MAX_REGISTROS} altas por hora.
 *       Lo que ya existía; no cambia.</li>
 *   <li>{@link Bucket#LOGIN} — {@value #MAX_LOGINS_FALLIDOS} <b>fallos</b> por
 *       ventana de {@value #MINUTOS_VENTANA_LOGIN} minutos. Ver abajo.</li>
 *   <li>{@link Bucket#RECUPERACION} — {@value #MAX_RECUPERACIONES} por hora.
 *       Sin cupo, este endpoint sirve para bombardear el buzón de un usuario y
 *       para enumerar cuentas a base de medir tiempos de respuesta.</li>
 * </ul>
 *
 * <h3>Por qué el login cuenta SOLO los fallos</h3>
 *
 * Los terminales de un local salen a internet por una sola IP. Si se contaran
 * todos los intentos, un negocio con tres cajas dejaría fuera a su propio
 * personal en un cambio de turno — y dejar sin vender a un cliente por
 * protegerlo de un ataque que no está ocurriendo es un mal negocio.
 *
 * Contando solo los fallos, quien teclea bien nunca acumula nada y el cupo solo
 * lo consume quien se equivoca. Un ataque de fuerza bruta es, por definición,
 * una sucesión de fallos: choca contra el muro de inmediato.
 *
 * Además, un login correcto {@link #limpiar limpia} el cupo de esa IP: si el
 * cajero se equivocó cuatro veces y a la quinta entró, no arrastra las cuatro.
 *
 * <h3>Por qué no se distingue por email</h3>
 *
 * Sería más fino, pero permitiría al atacante esquivar el cupo rotando el email
 * —que es justo lo que hace el credential stuffing—. La IP es la dimensión que
 * al atacante le cuesta cambiar. El coste es el del párrafo anterior, y por eso
 * se cuentan solo los fallos.
 */
@Component
@Profile("cloud")
public class RegisterRateLimiter {

    /** Qué se está limitando. Cada valor lleva su cuenta por separado. */
    public enum Bucket {
        REGISTRO,
        LOGIN,
        RECUPERACION
    }

    // --- Cupos -----------------------------------------------------------
    // El de registro se conserva tal cual estaba (5/hora): no es objeto de este
    // cambio y hay tests que dependen de él.

    static final int MAX_REGISTROS = 5;
    static final Duration VENTANA_REGISTRO = Duration.ofHours(1);

    static final int MAX_LOGINS_FALLIDOS = 10;
    static final int MINUTOS_VENTANA_LOGIN = 15;
    static final Duration VENTANA_LOGIN = Duration.ofMinutes(MINUTOS_VENTANA_LOGIN);

    static final int MAX_RECUPERACIONES = 5;
    static final Duration VENTANA_RECUPERACION = Duration.ofHours(1);

    /** Compatibilidad: el cupo de registro tal como lo nombraba la versión previa. */
    static final int MAX_PER_WINDOW = MAX_REGISTROS;
    static final Duration WINDOW = VENTANA_REGISTRO;

    /** Clave = "<bucket>|<ip>", para que los cupos no se pisen entre sí. */
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Registra un intento de ALTA para la IP; lanza 429 si excede el cupo.
     * Se conserva la firma original: es la que usa {@code /auth/register}.
     */
    public void check(String ip) {
        verificarCupo(Bucket.REGISTRO, ip);
        anotarIntento(Bucket.REGISTRO, ip);
    }

    /**
     * Comprueba el cupo SIN consumirlo. Lanza {@link AuthException} 429 si la IP
     * ya lo agotó.
     *
     * <p>Está separado de {@link #anotarIntento} a propósito: el login necesita
     * mirar antes de intentar y anotar solo si falló.
     */
    public void verificarCupo(Bucket bucket, String ip) {
        Deque<Instant> q = colaDe(bucket, ip);
        int max = maximoDe(bucket);
        synchronized (q) {
            purgar(q, bucket);
            if (q.size() >= max) {
                throw new AuthException(429, mensajeDe(bucket));
            }
        }
    }

    /** Consume una unidad del cupo de esa IP en ese bucket. */
    public void anotarIntento(Bucket bucket, String ip) {
        Deque<Instant> q = colaDe(bucket, ip);
        synchronized (q) {
            purgar(q, bucket);
            q.addLast(Instant.now());
        }
    }

    /**
     * Borra el cupo consumido por esa IP en ese bucket. Lo llama el login cuando
     * las credenciales son correctas: los fallos previos dejan de contar.
     */
    public void limpiar(Bucket bucket, String ip) {
        hits.remove(clave(bucket, ip));
    }

    // --- Interno ---------------------------------------------------------

    private Deque<Instant> colaDe(Bucket bucket, String ip) {
        return hits.computeIfAbsent(clave(bucket, ip), k -> new ArrayDeque<>());
    }

    private String clave(Bucket bucket, String ip) {
        String base = (ip == null || ip.isBlank()) ? "unknown" : ip;
        return bucket.name() + "|" + base;
    }

    /** Descarta los intentos que ya salieron de la ventana. */
    private void purgar(Deque<Instant> q, Bucket bucket) {
        Instant cutoff = Instant.now().minus(ventanaDe(bucket));
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }

    private int maximoDe(Bucket bucket) {
        return switch (bucket) {
            case REGISTRO -> MAX_REGISTROS;
            case LOGIN -> MAX_LOGINS_FALLIDOS;
            case RECUPERACION -> MAX_RECUPERACIONES;
        };
    }

    private Duration ventanaDe(Bucket bucket) {
        return switch (bucket) {
            case REGISTRO -> VENTANA_REGISTRO;
            case LOGIN -> VENTANA_LOGIN;
            case RECUPERACION -> VENTANA_RECUPERACION;
        };
    }

    /**
     * El mensaje no revela si el email existe ni cuántos intentos quedan: solo
     * que hay que esperar.
     */
    private String mensajeDe(Bucket bucket) {
        return switch (bucket) {
            case REGISTRO -> "Demasiados registros desde esta red; intenta más tarde.";
            case LOGIN -> "Demasiados intentos fallidos desde esta red; espera unos minutos.";
            case RECUPERACION -> "Demasiadas solicitudes desde esta red; intenta más tarde.";
        };
    }
}
