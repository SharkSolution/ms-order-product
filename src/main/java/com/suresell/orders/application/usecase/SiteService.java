package com.suresell.orders.application.usecase;

import com.suresell.orders.domain.model.Site;
import com.suresell.orders.infrastructure.persistence.SiteRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Sedes y modo de POS (Inc. 1 del modo Restaurante).
 *
 * El modo es **server-authoritative**: la UI lo refleja, no lo decide. Y solo
 * lo cambia el KAM, porque es parte de lo que se le vende al negocio, no una
 * preferencia del cliente.
 */
@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository repository;

    public List<Site> listar() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * Sede por defecto del negocio. Si el tenant todavía no tiene ninguna (p.ej.
     * se creó después de la migración), se devuelve vacío y el llamante trata el
     * caso como PLAZOLETA — nunca se inventa una sede.
     */
    public Optional<Site> sedePorDefecto() {
        return repository.findFirstByIsDefaultTrue();
    }

    /** Modo efectivo del negocio. Sin sede configurada ⇒ PLAZOLETA (lo de siempre). */
    public String modoEfectivo() {
        return sedePorDefecto().map(Site::getPosMode).orElse(Site.MODO_PLAZOLETA);
    }

    public boolean enModoRestaurante() {
        return Site.MODO_RESTAURANTE.equalsIgnoreCase(modoEfectivo());
    }

    /** Cambia el modo de una sede. Solo lo invoca el KAM. */
    @Transactional
    public Site cambiarModo(Long siteId, String modo) {
        String normalizado = modo == null ? "" : modo.trim().toUpperCase();
        if (!Site.MODO_PLAZOLETA.equals(normalizado) && !Site.MODO_RESTAURANTE.equals(normalizado)) {
            throw new IllegalArgumentException(
                    "Modo inválido. Use " + Site.MODO_PLAZOLETA + " o " + Site.MODO_RESTAURANTE);
        }
        Site sede = repository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la sede " + siteId));
        sede.setPosMode(normalizado);
        return repository.save(sede);
    }

    /**
     * Normaliza el código de la sede. Quita TILDES antes de filtrar: sin eso
     * "Sede Chicó" quedaba como "SEDE-CHIC-" (la ó se volvía guion y dejaba uno
     * colgando), y ese código es la base de la numeración de facturas.
     */
    static String normalizarCodigo(String texto) {
        String sinTildes = java.text.Normalizer
                .normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String code = sinTildes.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        return code.replaceAll("^-+|-+$", "");
    }

    /** Crea una sede adicional. El multisede completo queda fuera de alcance. */
    @Transactional
    public Site crear(String nombre, String codigo, String modo) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sede es obligatorio");
        }
        String code = normalizarCodigo(codigo == null || codigo.isBlank() ? nombre : codigo);
        if (repository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Ya existe una sede con el código " + code);
        }
        Site sede = new Site();
        sede.setName(nombre.trim());
        sede.setCode(code);
        sede.setPosMode(Site.MODO_RESTAURANTE.equalsIgnoreCase(modo)
                ? Site.MODO_RESTAURANTE : Site.MODO_PLAZOLETA);
        sede.setActive(true);
        sede.setIsDefault(repository.findFirstByIsDefaultTrue().isEmpty());
        return repository.save(sede);
    }
}
