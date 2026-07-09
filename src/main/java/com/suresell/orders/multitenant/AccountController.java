package com.suresell.orders.multitenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operaciones sobre la cuenta del usuario autenticado (perfil `cloud`). A
 * diferencia de {@link AuthController} (`/auth/**`, exento del filtro), esto vive
 * bajo `/account/**`, así que {@link TenantContextFilter} EXIGE un JWT válido: la
 * identidad (email + tenant) sale del token, no del body. Ver docs/110 §8.
 */
@RestController
@Profile("cloud")
public class AccountController {

    private final AuthService auth;
    private final JwtTenantResolver resolver;

    public AccountController(AuthService auth, JwtTenantResolver resolver) {
        this.auth = auth;
        this.resolver = resolver;
    }

    @PostMapping("/account/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req,
                                            HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        // El filtro ya validó el token; el tenant vive en el contexto del request.
        String tenantId = TenantContext.get();
        String email = resolver.resolveSubject(header).orElse(null);
        if (email == null || email.isBlank() || tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión inválida"));
        }
        try {
            auth.changePassword(email, tenantId, req.currentPassword(), req.newPassword());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (AuthException e) {
            return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
        }
    }

    /** Datos del negocio del tenant autenticado (para imprimirlos en el ticket). */
    @GetMapping("/account/business")
    public ResponseEntity<?> getBusiness() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión inválida"));
        }
        try {
            return ResponseEntity.ok(auth.getBusiness(tenantId));
        } catch (AuthException e) {
            return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/account/business")
    public ResponseEntity<?> updateBusiness(@RequestBody BusinessRequest req) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión inválida"));
        }
        try {
            return ResponseEntity.ok(auth.updateBusiness(tenantId, req.name(), req.nit(),
                    req.address(), req.phone(), req.ticketFooter(), req.editPassword()));
        } catch (AuthException e) {
            return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record BusinessRequest(String name, String nit, String address, String phone,
                                  String ticketFooter, String editPassword) {}
}
