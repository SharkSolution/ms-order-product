package com.suresell.orders.multitenant;

/**
 * Error de autenticación con un código HTTP explícito. El {@link AuthController}
 * lo mapea a la respuesta ({401} credenciales, {400} validación, {403} tenant
 * suspendido, {409} email/negocio ya existe). El mensaje es apto para el usuario
 * final — no revela si un email concreto existe (se usa uno genérico en login).
 */
public class AuthException extends RuntimeException {

    private final int status;

    public AuthException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
