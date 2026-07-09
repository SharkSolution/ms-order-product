package com.suresell.orders.multitenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hardening de auth (docs/110 §8): validación del secreto JWT y rate-limit de registro. */
class AuthHardeningTest {

    @Test
    void jwtSecretValidatorRejectsEmptyPlaceholderAndShort() {
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator(""));
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator(null));
        assertThrows(IllegalStateException.class,
                () -> new JwtSecretValidator(JwtSecretValidator.DEFAULT_PLACEHOLDER));
        assertThrows(IllegalStateException.class, () -> new JwtSecretValidator("corto"));
    }

    @Test
    void jwtSecretValidatorAcceptsStrongSecret() {
        assertDoesNotThrow(() -> new JwtSecretValidator("una-clave-secreta-de-mas-de-32-bytes-ok!!"));
    }

    @Test
    void rateLimiterBlocksAfterQuotaPerIp() {
        RegisterRateLimiter limiter = new RegisterRateLimiter();
        for (int i = 0; i < RegisterRateLimiter.MAX_PER_WINDOW; i++) {
            assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
        }
        AuthException ex = assertThrows(AuthException.class, () -> limiter.check("1.2.3.4"));
        assertEquals(429, ex.status());
    }

    @Test
    void rateLimiterIsPerIp() {
        RegisterRateLimiter limiter = new RegisterRateLimiter();
        for (int i = 0; i < RegisterRateLimiter.MAX_PER_WINDOW; i++) {
            limiter.check("1.1.1.1");
        }
        // Otra IP no se ve afectada por el cupo de la primera.
        assertDoesNotThrow(() -> limiter.check("9.9.9.9"));
    }
}
