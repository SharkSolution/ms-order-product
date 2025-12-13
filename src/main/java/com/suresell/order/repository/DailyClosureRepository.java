/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.entity.DailyClosure
 *  com.suresell.order.repository.DailyClosureRepository
 *  org.springframework.data.jpa.repository.JpaRepository
 *  org.springframework.data.jpa.repository.Query
 *  org.springframework.stereotype.Repository
 */
package com.suresell.order.repository;
import com.suresell.order.model.entity.DailyClosure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DailyClosureRepository
extends JpaRepository<DailyClosure, UUID> {
    @Query(value="SELECT dc FROM DailyClosure dc ORDER BY dc.closingTime DESC LIMIT 1")
    public Optional<DailyClosure> findLastClosure();
    @Query(value="SELECT dc FROM DailyClosure dc ORDER BY dc.closingTime DESC")
    public List<DailyClosure> findAllClosuresOrderByDateDesc();
}
