package com.suresell.orders.infrastructure.web;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * La cabecera {@code Authorization} de la petición HTTP en curso, si la hay.
 *
 * <p><b>Para qué.</b> El cierre de caja consulta los pagos QR a `ms-core-app`, y
 * ese servicio exige un JWT de negocio desde el 2026-07-30. El token ya viene en
 * la petición del cajero; lo único que faltaba era llevarlo hasta la llamada
 * saliente.
 *
 * <p><b>Por qué un componente y no leer el header en el caso de uso.</b> Leer
 * {@code RequestContextHolder} directamente desde la lógica de negocio la ata al
 * hilo de Tomcat y la vuelve intestable sin levantar medio Spring. Envuelto
 * aquí, el caso de uso recibe una dependencia normal que en un test se sustituye
 * por una lambda.
 *
 * <p><b>Sin petición en curso devuelve vacío</b> —arranque, tarea de fondo, un
 * test— y el llamador decide qué hacer. Lo que NO hace es inventarse un token:
 * una llamada sin credencial que falla con 401 es un caso degradado honesto y
 * queda registrado como tal; una llamada con un token fabricado sería un agujero.
 */
@Component
public class TokenDeLaPeticion {

    /**
     * @return el valor completo de la cabecera {@code Authorization}
     *         (incluido el prefijo {@code Bearer }), o vacío si no hay petición
     *         en curso o la petición no la trae.
     */
    public Optional<String> cabeceraAuthorization() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes sra)) {
                return Optional.empty();
            }
            String cabecera = sra.getRequest().getHeader("Authorization");
            if (cabecera == null || cabecera.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(cabecera);
        } catch (Exception e) {
            // Esto corre dentro de un cierre de caja: un fallo leyendo una
            // cabecera no puede tumbar la operación. Sin token, el conciliador
            // registra el caso degradado.
            return Optional.empty();
        }
    }
}
