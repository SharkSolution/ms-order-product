package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.DailyClosure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
@Repository
public interface DailyClosureRepository
extends JpaRepository<DailyClosure, UUID> {
    public Optional<DailyClosure> findTopByOrderByClosingTimeDesc();

    @Query(value="SELECT dc FROM DailyClosure dc ORDER BY dc.closingTime DESC")
    public List<DailyClosure> findAllClosuresOrderByDateDesc();
}
