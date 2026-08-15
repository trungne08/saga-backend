package com.saga.be.integration.write;

import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.repository.TaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a Jira-canonical Task after an independent upsert has committed.
 * This is deliberately a separate bean so Spring opens a fresh transaction
 * instead of reusing an outer repeatable-read snapshot.
 */
@Service
public class JiraCanonicalTaskReadService {

    private final TaskRepository taskRepository;

    public JiraCanonicalTaskReadService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<TaskReadResponse> findResponse(UUID projectId, String externalId) {
        return taskRepository.findByProjectIdAndExternalId(projectId, externalId)
                .map(TaskReadResponse::from);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean exists(UUID projectId, String externalId) {
        return taskRepository.findByProjectIdAndExternalId(projectId, externalId).isPresent();
    }
}
