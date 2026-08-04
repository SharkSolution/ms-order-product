package com.suresell.orders.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La hora del negocio no puede depender de una variable de entorno.
 *
 * <p>Bogotá es UTC−5: <b>desde las 7 de la noche el servidor ya está en el día
 * siguiente</b>. En Producción la variable {@code TZ} de {@code ms-order-product}
 * se perdió y nadie se enteró — el monto de QR dejó de autocompletarse en el
 * cierre nocturno porque el servicio buscaba el día equivocado.
 *
 * <p>El segundo test es el que importa: barre el código fuente buscando
 * {@code LocalDate.now()} sin zona. Un comentario que diga "usar ZonaHoraria" no
 * impide nada; esto sí.
 */
class ZonaHorariaTest {

    @Test
    @DisplayName("la fecha del negocio es la de Bogotá, aunque el servidor esté en otra zona")
    void laFechaEsLaDeBogotaSinImportarElServidor() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Servidor en UTC, que es como corre Railway sin la variable TZ.
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            assertThat(ZonaHoraria.hoy()).isEqualTo(LocalDate.now(ZoneId.of("America/Bogota")));
            assertThat(ZonaHoraria.ahora().getHour())
                    .isEqualTo(java.time.LocalDateTime.now(ZoneId.of("America/Bogota")).getHour());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("en Tokio tampoco cambia: la zona no se hereda del sistema")
    void tampocoDependeDeUnaZonaExotica() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertThat(ZonaHoraria.hoy()).isEqualTo(LocalDate.now(ZoneId.of("America/Bogota")));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("GUARDA: ningún archivo usa LocalDate.now() sin zona")
    void nadieUsaLaFechaDelSistema() throws IOException {
        Pattern sinZona = Pattern.compile("Local(Date|DateTime)\\.now\\(\\s*\\)");
        List<String> infractores = new ArrayList<>();

        try (Stream<Path> archivos = Files.walk(Path.of("src/main/java"))) {
            archivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("ZonaHoraria.java"))
                    .forEach(p -> {
                        try {
                            if (sinZona.matcher(Files.readString(p)).find()) {
                                infractores.add(p.getFileName().toString());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertThat(infractores)
                .as("Usan la fecha del SISTEMA en vez de la del negocio. "
                        + "Después de las 7 p.m. eso es el día equivocado. "
                        + "Reemplazar por ZonaHoraria.hoy() / ZonaHoraria.ahora().")
                .isEmpty();
    }
}
