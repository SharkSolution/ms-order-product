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
    /**
     * El cierre anterior: de aquí salen a la vez la BASE del día y el arranque
     * de la ventana. Tienen que salir de la misma fila.
     *
     * <p>Aquí vivía también un {@code findLastClosingTimeByUser(userName)} que
     * hacía {@code SELECT MAX(closing_time) ... WHERE user_name = :userName}.
     * Se eliminó el 2026-08-31 porque era una trampa: un {@code MAX} sin
     * {@code GROUP BY} devuelve siempre una fila, con NULL dentro si no encaja
     * nadie, y Spring Data convierte ese NULL en {@code Optional.empty()}. Es
     * decir, <b>«no hay cierre anterior» y «el nombre no coincide con ninguno»
     * son indistinguibles</b> para quien llama. El POS mandaba
     * {@code sellerId: 'Angie'} mientras los cierres se guardaban con
     * {@code user_name = 'Cajero 1'}, y durante 103 cierres el resultado fue
     * silenciosamente el segundo caso.
     */
    Optional<DailyClosure> findFirstByOrderByClosingTimeDesc();
}
