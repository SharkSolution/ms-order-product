package com.suresell.orders.application.usecase;

import com.suresell.orders.application.dto.PagerGroupDto;
import com.suresell.orders.domain.model.TenantPagerGroup;
import com.suresell.orders.infrastructure.persistence.TenantPagerGroupRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Configuración de rastreadores por negocio (N2/6.7): cantidad por grupo y
 * nombre/color de cada grupo. Antes esto estaba quemado (2 grupos x 16).
 *
 * El `code` del grupo NO se edita: es lo que queda escrito en
 * `orders.pager_color` y hay historial con esos valores. Editar el nombre
 * cambia solo lo que ve el cajero.
 */
@Service
@RequiredArgsConstructor
public class PagerConfigService {

    /** Config por defecto si el tenant todavía no tiene filas (misma de siempre). */
    private static final List<PagerGroupDto> DEFAULT_GROUPS = List.of(
            new PagerGroupDto("AMARILLO", "Amarillo", "#eab308", 16),
            new PagerGroupDto("AZUL", "Azul", "#3b82f6", 16));

    private static final int MAX_QUANTITY = 200;

    private final TenantPagerGroupRepository repository;

    /** Grupos del tenant; si no hay nada configurado, devuelve el default histórico. */
    public List<PagerGroupDto> getGroups() {
        List<TenantPagerGroup> stored = repository.findAllByOrderBySortOrderAscIdAsc();
        if (stored.isEmpty()) {
            return DEFAULT_GROUPS;
        }
        return stored.stream()
                .map(g -> new PagerGroupDto(g.getCode(), g.getLabel(), g.getColor(), g.getQuantity()))
                .toList();
    }

    /**
     * Actualiza nombre, color y cantidad de los grupos existentes. NO crea ni
     * borra grupos: alterar el conjunto de códigos dejaría huérfanas las órdenes
     * históricas que apuntan a ellos.
     */
    @Transactional
    public List<PagerGroupDto> updateGroups(List<PagerGroupDto> groups) {
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un grupo de rastreadores");
        }
        for (PagerGroupDto dto : groups) {
            if (dto.code() == null || dto.code().isBlank()) {
                throw new IllegalArgumentException("Cada grupo debe traer su código");
            }
            if (dto.label() == null || dto.label().isBlank()) {
                throw new IllegalArgumentException("El nombre del grupo no puede quedar vacío");
            }
            if (dto.quantity() == null || dto.quantity() < 0 || dto.quantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException(
                        "La cantidad de rastreadores debe estar entre 0 y " + MAX_QUANTITY);
            }
            TenantPagerGroup entity = repository.findByCode(dto.code())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el grupo de rastreadores: " + dto.code()));
            entity.setLabel(dto.label().trim());
            entity.setColor(dto.color() == null || dto.color().isBlank() ? "#64748b" : dto.color().trim());
            entity.setQuantity(dto.quantity());
            repository.save(entity);
        }
        return getGroups();
    }
}
