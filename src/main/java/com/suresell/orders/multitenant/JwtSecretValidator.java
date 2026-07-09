package com.suresell.orders.multitenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Falla el arranque (perfil `cloud`) si {@code security.jwt.secret} (env JWT_SECRET)
 * está vacío, es el placeholder por defecto, o es más corto que 32 bytes (HS512
 * exige clave robusta). Evita desplegar producción con un secreto adivinable con el
 * que cualquiera podría forjar JWTs de cualquier tenant. Ver docs/110 §8.
 */
@Component
@Profile("cloud")
public class JwtSecretValidator {

    static final String DEFAULT_PLACEHOLDER = "cambia-esta-clave-en-produccion-min-32-bytes!";

    public JwtSecretValidator(@Value("${security.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET (security.jwt.secret) es obligatorio en el perfil cloud.");
        }
        if (DEFAULT_PLACEHOLDER.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET tiene el valor placeholder por defecto; define uno propio y secreto.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 32 bytes (256 bits).");
        }
    }
}
