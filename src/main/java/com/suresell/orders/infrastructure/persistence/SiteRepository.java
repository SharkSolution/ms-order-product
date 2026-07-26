package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.Site;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sedes del tenant (RLS acota al tenant de la sesión). */
public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findAllByOrderByIdAsc();
    Optional<Site> findFirstByIsDefaultTrue();
    Optional<Site> findByCode(String code);
}
