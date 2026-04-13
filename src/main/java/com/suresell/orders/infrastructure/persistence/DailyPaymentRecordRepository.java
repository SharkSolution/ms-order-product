package com.suresell.orders.infrastructure.persistence;

import com.suresell.orders.domain.model.DailyPaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyPaymentRecordRepository extends JpaRepository<DailyPaymentRecord, UUID> {

    Optional<DailyPaymentRecord> findByRecordDateAndPaymentMethod(LocalDate recordDate, String paymentMethod);

    List<DailyPaymentRecord> findByRecordDate(LocalDate recordDate);

    @Query("SELECT d FROM DailyPaymentRecord d WHERE d.recordDate = :date AND d.paymentMethod = 'QR'")
    Optional<DailyPaymentRecord> findQrByDate(@Param("date") LocalDate date);

    @Query("SELECT d FROM DailyPaymentRecord d WHERE d.recordDate BETWEEN :startDate AND :endDate ORDER BY d.recordDate DESC")
    List<DailyPaymentRecord> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
