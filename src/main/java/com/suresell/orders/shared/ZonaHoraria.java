package com.suresell.orders.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * La hora del negocio. Colombia, siempre.
 *
 * <p><b>Por qué existe.</b> Bogotá es UTC−5, así que <b>desde las 7 de la noche
 * el servidor ya está en el día siguiente</b>. Un {@code LocalDate.now()} pelado
 * toma la zona del sistema, y en Railway eso es UTC salvo que alguien acuerde de
 * poner la variable {@code TZ} — que es exactamente lo que se perdió en
 * {@code ms-order-product} sin que nadie se enterara: el monto de QR dejó de
 * autocompletarse en el cierre nocturno porque el servicio buscaba el día
 * equivocado.
 *
 * <p>La zona es una regla del negocio, no configuración del entorno. Vive acá
 * para que no dependa de que una variable siga puesta.
 *
 * <p><b>Regla</b>: en este proyecto {@code LocalDate.now()} y
 * {@code LocalDateTime.now()} sin zona son un error. Usar {@link #hoy()} y
 * {@link #ahora()}.
 */
public final class ZonaHoraria {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private ZonaHoraria() {
    }

    /** El día de hoy para el negocio. */
    public static LocalDate hoy() {
        return LocalDate.now(BOGOTA);
    }

    /** El instante actual en hora del negocio. */
    public static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
