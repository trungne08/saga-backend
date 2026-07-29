package com.saga.be.repository;

import com.saga.be.entity.Sprint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    Optional<Sprint> findByBoardIdAndExternalSprintId(
            UUID boardId,
            String externalSprintId
    );
}
