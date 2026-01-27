package com.suresell.order.model.record;

import java.util.List;

/**
 * DTO ligero para paginación.
 * Evita serializar Pageable, Sort y metadata redundante de PageImpl.
 * Reduce serialización de ~3s a ~500ms.
 */
public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int size,
    int number,
    boolean last
) {

    /**
     * Factory method para crear desde Spring Page.
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getSize(),
            page.getNumber(),
            page.isLast()
        );
    }
}
