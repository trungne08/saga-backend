package com.saga.be.integration.write;

import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies a confirmed Jira Sprint target without reusing an outer read snapshot. */
@Service
public class JiraTaskSprintFinalizationService {

    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;

    public JiraTaskSprintFinalizationService(TaskRepository taskRepository, SprintRepository sprintRepository) {
        this.taskRepository = taskRepository;
        this.sprintRepository = sprintRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyTarget(UUID projectId, String externalTaskId, UUID targetSprintId) {
        Task task = taskRepository.findByProjectIdAndExternalId(projectId, externalTaskId).orElseThrow(() ->
                IntegrationException.conflict("JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery")
        );
        if (targetSprintId == null) {
            task.setSprint(null);
        } else {
            Sprint sprint = sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(targetSprintId, projectId)
                    .orElseThrow(() -> IntegrationException.conflict(
                            "JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery"
                    ));
            task.setSprint(sprint);
        }
        taskRepository.saveAndFlush(task);
    }
}
