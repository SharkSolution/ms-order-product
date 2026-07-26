package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.RestaurantTable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Mesas del tenant (RLS acota al tenant de la sesión). */
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findAllByOrderByNumberAsc();
    List<RestaurantTable> findByActiveTrueOrderByNumberAsc();
    Optional<RestaurantTable> findByNumber(Integer number);
    long countByActiveTrue();
}
