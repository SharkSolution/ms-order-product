package com.suresell.orders.domain.service;

import com.suresell.orders.application.dto.ClosurePreviewResponse;
import com.suresell.orders.application.dto.ClosureRequest;
import com.suresell.orders.application.dto.ClosureResponse;
import com.suresell.orders.domain.port.in.DailyClosurePort;
import com.suresell.orders.infrastructure.persistence.repository.DailyClosureJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DailyClosureDomainService implements DailyClosurePort {

    private final DailyClosureJpaRepository dailyClosureJpaRepository;

    public DailyClosureDomainService(DailyClosureJpaRepository dailyClosureJpaRepository) {
        this.dailyClosureJpaRepository = dailyClosureJpaRepository;
    }

    @Override
    public ClosurePreviewResponse getClosurePreview() {
        // TODO: Implement logic
        return null;
    }

    @Override
    public ClosureResponse executeClosure(ClosureRequest request) {
        // TODO: Implement logic
        return null;
    }

    @Override
    public List<ClosureResponse> getAllClosures() {
        // TODO: Implement logic
        return List.of();
    }
}
