package com.saga.be.repository;

import com.saga.be.entity.GraphProcessingRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphProcessingRunRepository extends JpaRepository<GraphProcessingRun, UUID> {

    List<GraphProcessingRun> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAsc(
            LocalDateTime startInclusive,
            LocalDateTime endInclusive
    );

    Optional<GraphProcessingRun> findTopByOrderByOccurredAtAsc();
}
