package com.suresell.orders.multitenant;

/**
 * Marca una entidad JPA que pertenece a un tenant (columna `tenant_id`).
 *
 * La implementan las entidades de negocio para que {@link TenantEntityListener}
 * pueble el `tenant_id` al insertar, tomándolo del {@link TenantContext} del
 * request. En el arranque local-first (perfil por defecto, sin filtro de tenant)
 * el contexto está vacío y el campo queda nulo — aditivo, no rompe SQLite.
 * Ver docs/40-multitenant.md.
 */
public interface TenantOwned {

    String getTenantId();

    void setTenantId(String tenantId);
}
