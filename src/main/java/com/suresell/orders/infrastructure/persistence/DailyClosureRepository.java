package com.suresell.orders.infrastructure.persistence;
import com.suresell.orders.domain.model.DailyClosure;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
@Repository
public interface DailyClosureRepository
extends JpaRepository<DailyClosure, UUID> {
    Optional<DailyClosure> findTopByOrderByClosingTimeDesc();
    @Query(value="SELECT dc FROM DailyClosure dc ORDER BY dc.closingTime DESC")
    List<DailyClosure> findAllClosuresOrderByDateDesc();
    @Query("SELECT MAX(c.closingTime) FROM DailyClosure c WHERE (:userName IS NULL OR c.userName = :userName)")
    Optional<LocalDateTime> findLastClosingTimeByUser(@Param("userName") String userName);
}
