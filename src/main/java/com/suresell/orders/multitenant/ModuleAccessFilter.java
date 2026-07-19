package com.suresell.orders.multitenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Enforcement de módulos por plan (F3, docs/160 / docs/50 §4): el backend HACE
 * CUMPLIR qué módulos tiene el tenant, no solo la UI. Si el path pertenece a un
 * módulo que el tenant NO tiene (según el claim `modules` del JWT), responde 403.
 *
 * SOLO perfil `cloud`. Backward-compat: si el token no trae el claim `modules`
 * (tokens viejos), `resolveModules` devuelve vacío y NO se bloquea, hasta que todos
 * re-loguean. El {@link TenantContextFilter} sigue siendo quien exige un JWT válido.
 */
@Component
@Profile("cloud")
public class ModuleAccessFilter extends OncePerRequestFilter {

    /** Prefijo de path → módulo requerido. Se irá extendiendo por módulo. */
    private static final Map<String, String> PATH_MODULE = Map.of(
            "/api/discounts", PlanCatalog.DESCUENTOS);

    private final JwtTenantResolver resolver;

    public ModuleAccessFilter(JwtTenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(req)) {
            chain.doFilter(req, res);
            return;
        }
        String required = requiredModule(req.getRequestURI());
        if (required != null) {
            List<String> modules = resolver.resolveModules(req.getHeader("Authorization"));
            // Vacío = token sin claim (viejo) → no bloquear (backward-compat).
            if (!modules.isEmpty() && !modules.contains(required)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Tu plan no incluye el módulo: " + required);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private String requiredModule(String path) {
        if (path == null) {
            return null;
        }
        for (Map.Entry<String, String> e : PATH_MODULE.entrySet()) {
            if (path.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }
}
