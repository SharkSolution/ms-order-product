package com.suresell.orders.multitenant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ver {@link ResetLinkBaseValidator} para el porqué. */
class ResetLinkBaseValidatorTest {

    @Test
    @DisplayName("sin la variable, el servicio no arranca")
    void vacioNoArranca() {
        for (String valor : new String[] {null, "", "   "}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ResetLinkBaseValidator(valor));
            assertTrue(e.getMessage().contains("AUTH_RESET_LINK_BASE"),
                    "el mensaje debe nombrar la variable que hay que poner: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("una base sin esquema tampoco arranca: produciría un enlace relativo")
    void sinEsquemaNoArranca() {
        for (String valor : new String[] {"pos-caja.suresell.com.co", "/reset", "www.ejemplo.co"}) {
            assertThrows(IllegalStateException.class, () -> new ResetLinkBaseValidator(valor),
                    "se aceptó una base sin esquema: " + valor);
        }
    }

    @Test
    @DisplayName("una URL absoluta arranca, con o sin barra final")
    void urlAbsolutaArranca() {
        assertDoesNotThrow(() -> new ResetLinkBaseValidator("https://pos-caja.suresell.com.co"));
        assertDoesNotThrow(() -> new ResetLinkBaseValidator("https://pos-caja.suresell.com.co/"));
        // La de Railway sigue siendo válida: el validador comprueba la FORMA, no
        // qué dominio es. Elegir el dominio es una decisión de despliegue y
        // meterla aquí sería el error que este validador documenta.
        assertDoesNotThrow(
                () -> new ResetLinkBaseValidator("https://pos-web-production-d00c.up.railway.app"));
        assertDoesNotThrow(() -> new ResetLinkBaseValidator("http://localhost:4200"));
    }
}
