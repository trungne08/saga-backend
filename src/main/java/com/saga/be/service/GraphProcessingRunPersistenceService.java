package com.saga.be.service;

import com.saga.be.entity.GraphProcessingRun;
import com.saga.be.repository.GraphProcessingRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class GraphProcessingRunPersistenceService {

    private final GraphProcessingRunRepository graphProcessingRunRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(GraphProcessingRun run) {
        graphProcessingRunRepository.saveAndFlush(run);
    }
}
