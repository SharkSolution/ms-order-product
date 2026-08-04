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

    private static final int MAX_QUANTITY = 200;

    private final TenantPagerGroupRepository repository;

    /**
     * Los rastreadores QUE CONFIGURÓ ESTE NEGOCIO. Vacío si no usa.
     *
     * <p>Antes, un negocio sin filas recibía "Amarillo y Azul, 16 de cada uno":
     * los de un cliente, entregados por defecto a todos. Un local que no usa
     * rastreadores igual los veía, y uno que usa otros colores tenía que
     * empezar por corregir los ajenos.
     *
     * <p>V31 guardó esa configuración como DATO para los negocios que ya
     * dependían de ella, así que quitarla del código no le cambia nada a nadie.
     * Los negocios nuevos arrancan sin rastreadores y eligen si los quieren.
     */
    public List<PagerGroupDto> getGroups() {
        List<TenantPagerGroup> stored = repository.findAllByOrderBySortOrderAscIdAsc();
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
