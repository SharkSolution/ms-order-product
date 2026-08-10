package com.suresell.orders.multitenant;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * DataSource que fija `app.tenant_id` (variable de sesión Postgres) en cada
 * conexión que entrega, tomándolo del {@link TenantContext} del request. Es la
 * pieza que hace que Row-Level Security aísle por tenant en el perfil `cloud`.
 *
 * Se fija a nivel de SESIÓN (is_local = false) en el checkout de la conexión, de
 * modo que aplica a TODAS las sentencias del request — con o sin transacción
 * explícita, y también bajo open-in-view. Como la conexión es exclusiva mientras
 * está prestada y SIEMPRE se re-fija (o se limpia a '') en el próximo checkout,
 * no hay fuga entre tenants al reutilizar conexiones del pool.
 *
 * IMPORTANTE (pooling): la variable de sesión sobrevive entre transacciones solo
 * en conexión directa o pooler en modo *session*. NO usar el pooler en modo
 * *transaction* (p. ej. Supabase puerto 6543) para el backend cloud: ahí la
 * sesión se reinicia por transacción y RLS dejaría de ver datos. Usar la
 * conexión directa / session-pooler (puerto 5432). Ver docs/40-multitenant.md.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private Connection applyTenant(Connection connection) throws SQLException {
        String tenantId = TenantContext.get();
        try (PreparedStatement ps =
                connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            // Sin tenant en contexto (health checks, arranque, tareas de fondo) se
            // limpia a '': RLS no ve ninguna fila (default seguro), nunca las de otro.
            //
            // OJO: eso vale para LEER. Para ESCRIBIR, '' no es lo mismo que NULL —
            // `'' = ''` es cierto, así que un WITH CHECK contra la cadena vacía pasa.
            // Por eso el default de `tenant_id` en la base va envuelto en `nullif`
            // (V32): sin él, una fila sin negocio entraba con tenant_id = ''.
            ps.setString(1, tenantId == null ? "" : tenantId);
            ps.execute();
        }
        return connection;
    }
}
