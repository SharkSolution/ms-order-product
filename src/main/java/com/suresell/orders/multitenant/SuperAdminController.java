package com.suresell.orders.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API del panel de super-admin (KAM) GLOBAL. Bajo `/admin/**`, exento del filtro de
 * tenant (no está scopeado por tenant): `/admin/login` es público (emite el JWT de
 * super-admin) y el resto exige un JWT de super-admin válido. F3, Inc.3, docs/160.
 */
@RestController
@Profile("cloud")
public class SuperAdminController {

    private final SuperAdminService svc;
    private final JwtTenantResolver resolver;

    public SuperAdminController(SuperAdminService svc, JwtTenantResolver resolver) {
        this.svc = svc;
        this.resolver = resolver;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(svc.login(req.email(), req.password()));
        } catch (AuthException e) {
            return err(e);
        }
    }

    @GetMapping("/admin/tenants")
    public ResponseEntity<?> listTenants(HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        return guard != null ? guard : ResponseEntity.ok(svc.listTenants());
    }

    @GetMapping("/admin/tenants/{id}")
    public ResponseEntity<?> getTenant(@PathVariable String id, HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        if (guard != null) {
            return guard;
        }
        try {
            return ResponseEntity.ok(svc.getTenant(id));
        } catch (AuthException e) {
            return err(e);
        }
    }

    @PutMapping("/admin/tenants/{id}/plan")
    public ResponseEntity<?> setPlan(@PathVariable String id, @RequestBody PlanRequest req,
                                     HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        if (guard != null) {
            return guard;
        }
        try {
            svc.setPlan(id, req.plan());
            return ResponseEntity.ok(svc.getTenant(id));
        } catch (AuthException e) {
            return err(e);
        }
    }

    @PutMapping("/admin/tenants/{id}/modules")
    public ResponseEntity<?> setModules(@PathVariable String id, @RequestBody ModulesRequest req,
                                        HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        if (guard != null) {
            return guard;
        }
        try {
            return ResponseEntity.ok(svc.setModules(id, req.overrides()));
        } catch (AuthException e) {
            return err(e);
        }
    }

    /** Sedes de un negocio y su modo de POS (Inc. 1 del modo Restaurante). */
    @GetMapping("/admin/tenants/{id}/sites")
    public ResponseEntity<?> getSites(@PathVariable String id, HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        if (guard != null) {
            return guard;
        }
        try {
            return ResponseEntity.ok(svc.getSites(id));
        } catch (AuthException e) {
            return err(e);
        }
    }

    /** Cambiar el modo de una sede. SOLO el KAM: el modo se vende, no se elige. */
    @PutMapping("/admin/tenants/{id}/sites/{siteId}/mode")
    public ResponseEntity<?> setSiteMode(@PathVariable String id, @PathVariable Long siteId,
                                         @RequestBody SiteModeRequest req, HttpServletRequest http) {
        ResponseEntity<?> guard = requireSuper(http);
        if (guard != null) {
            return guard;
        }
        try {
            return ResponseEntity.ok(svc.setSiteMode(id, siteId, req.posMode()));
        } catch (AuthException e) {
            return err(e);
        }
    }

    public record SiteModeRequest(String posMode) {
    }

    private ResponseEntity<?> requireSuper(HttpServletRequest http) {
        if (!resolver.isSuperAdmin(http.getHeader("Authorization"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere super-admin"));
        }
        return null;
    }

    private ResponseEntity<?> err(AuthException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    public record LoginRequest(String email, String password) {}

    public record PlanRequest(String plan) {}

    public record ModulesRequest(Map<String, Boolean> overrides) {}
}
