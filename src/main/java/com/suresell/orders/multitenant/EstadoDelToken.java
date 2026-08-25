package com.suresell.orders.multitenant;

/**
 * Por qué un token de recuperación no sirve. Enum <b>CERRADO</b>, sin valor
 * "otro" (regla 10 de LINEAMIENTOS_DESARROLLO_DATA_FIRST).
 *
 * <h3>Por qué existe</h3>
 *
 * {@code AuthService.resetPassword} devolvía —y sigue devolviendo— el mismo
 * mensaje al usuario para los cuatro casos: <i>"Enlace inválido o expirado"</i>.
 * <b>Eso es correcto de cara afuera</b> y no se toca: distinguirlos en la
 * respuesta HTTP convertiría el endpoint en un oráculo para averiguar qué
 * tokens existen.
 *
 * <p>Pero hacia dentro esa ambigüedad no tiene ninguna ventaja y sí un coste:
 * cuando alguien reporta "el enlace me dice que está vencido", hoy no hay forma
 * de saber si de verdad caducó, si ya se había usado, o si el token que llegó
 * no es el que se emitió. Los tres se ven igual en el log y en la base.
 *
 * <p>El dato que lo motiva: en Producción hay <b>cuatro tokens en toda la
 * historia y ninguno se ha usado nunca</b> (medido el 2026-08-25). No prueba
 * que el flujo esté roto —cuatro intentos pueden ser cuatro pruebas
 * abandonadas— pero significa que nunca se ha completado con éxito, y hoy no
 * hay manera de averiguar por qué.
 */
public enum EstadoDelToken {

    /** El hash no está en {@code password_resets}. Enlace de otro despliegue,
     *  token truncado por el cliente de correo, o simplemente inventado. */
    no_existe,

    /** Existe, no se ha usado y no ha caducado. El único que deja seguir. */
    valido,

    /** Existe y {@code expires_at <= now()}. La ventana es de una hora. */
    vencido,

    /** Existe y ya se consumió. Un enlace de un solo uso, usado dos veces. */
    usado
}
