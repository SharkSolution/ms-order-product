package com.suresell.orders.domain.port.out;
import com.suresell.orders.domain.model.DailyClosure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface DailyClosureRepositoryPort {
    DailyClosure save(DailyClosure dailyClosure);
    Optional<DailyClosure> findById(UUID id);
    List<DailyClosure> findAll();
    Optional<DailyClosure> findLastClosure();
    List<DailyClosure> findAllClosuresOrderByDateDesc();
}
