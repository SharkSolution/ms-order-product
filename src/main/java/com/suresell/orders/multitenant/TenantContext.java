package com.suresell.orders.multitenant;

/**
 * Contexto del tenant activo para el request en curso (ThreadLocal). Lo fija el
 * TenantContextFilter a partir del JWT y lo consume la capa de datos para
 * establecer `app.tenant_id` (RLS). Ver docs/40-multitenant.md.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
