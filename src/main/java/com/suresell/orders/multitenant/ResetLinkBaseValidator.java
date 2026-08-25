package com.suresell.orders.multitenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Falla el arranque (perfil {@code cloud}) si {@code auth.reset.link-base}
 * (env {@code AUTH_RESET_LINK_BASE}) está vacío o no es una URL absoluta.
 *
 * <h3>El fallo silencioso que evita</h3>
 *
 * {@code AuthService.java:45-46} declara la propiedad con default vacío
 * ({@code @Value("${auth.reset.link-base:}")}). Con ese default,
 * {@code buildResetLink} ({@code AuthService.java:343-346}) devuelve
 * <b>{@code "/reset?token=…"}</b> — una URL relativa. El correo sale con un
 * enlace que no lleva a ninguna parte.
 *
 * <p>Y nadie se entera: {@code sendResetEmail} ({@code AuthService.java:364-366})
 * atrapa toda excepción con un catch vacío, porque un fallo de correo no debe
 * romper el flujo. Así que el usuario recibe un 200, el correo se manda, el
 * enlace está roto, y en la base queda una fila de {@code password_resets}
 * idéntica a la de un envío correcto.
 *
 * <p>Eso es peor que un error: con la variable puesta y con la variable ausente,
 * <b>todo lo observable se ve igual</b> —mismo código HTTP, misma fila, mismo
 * correo enviado—. La única forma de notarlo es que alguien intente usar el
 * enlace y avise. Por eso la comprobación va en el arranque y no en un log.
 *
 * <p>El mismo criterio y la misma forma que {@link JwtSecretValidator}.
 *
 * <h3>Por qué no se pone un default con el dominio</h3>
 *
 * Sería repetir el problema de {@code web_panel}, que lleva
 * {@code ENV LEGACY_DATA_TENANT=shark-burger} y la URL del panel dentro de su
 * propio {@code Dockerfile}: el día que el dominio cambia, el valor equivocado
 * sigue viajando en la imagen y funciona lo bastante como para no llamar la
 * atención. Un dominio es configuración de despliegue, no del código.
 */
@Component
@Profile("cloud")
public class ResetLinkBaseValidator {

    public ResetLinkBaseValidator(@Value("${auth.reset.link-base:}") String base) {
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    "AUTH_RESET_LINK_BASE (auth.reset.link-base) es obligatorio en el perfil "
                            + "cloud: sin ella los correos de recuperación salen con un enlace "
                            + "relativo (/reset?token=…) que no lleva a ninguna parte, y el "
                            + "envío se ve idéntico a uno correcto.");
        }
        String limpia = base.trim();
        if (!limpia.startsWith("http://") && !limpia.startsWith("https://")) {
            throw new IllegalStateException(
                    "AUTH_RESET_LINK_BASE debe ser una URL absoluta con esquema (https://…); "
                            + "llegó algo que no lo es y produciría un enlace roto.");
        }
    }
}
