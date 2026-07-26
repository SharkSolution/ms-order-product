package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.TenantPagerGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Grupos de rastreadores del tenant (RLS acota al tenant). */
public interface TenantPagerGroupRepository extends JpaRepository<TenantPagerGroup, Long> {
    List<TenantPagerGroup> findAllByOrderBySortOrderAscIdAsc();
    Optional<TenantPagerGroup> findByCode(String code);
}
