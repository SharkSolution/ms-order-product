package com.suresell.orders.multitenant;

import jakarta.persistence.PrePersist;

/**
 * Entity listener que puebla `tenant_id` al insertar cualquier entidad
 * {@link TenantOwned}, tomándolo del {@link TenantContext} del request.
 *
 * Se registra con {@code @EntityListeners(TenantEntityListener.class)} en cada
 * entidad de negocio. Es seguro en AMBOS perfiles:
 *  - Perfil `cloud`: el TenantContextFilter fijó el tenant → se puebla.
 *  - Perfil local-first (por defecto): no hay filtro → TenantContext está vacío
 *    → no toca nada → `tenant_id` queda nulo (columna nullable en SQLite).
 *
 * Solo asigna si el campo aún está vacío, para no pisar un tenant fijado
 * explícitamente (p. ej. sync inversa desde la nube). Ver docs/40-multitenant.md.
 */
public class TenantEntityListener {

    @PrePersist
    public void assignTenant(Object entity) {
        if (entity instanceof TenantOwned owned && owned.getTenantId() == null) {
            String tenantId = TenantContext.get();
            if (tenantId != null) {
                owned.setTenantId(tenantId);
            }
        }
    }
}
