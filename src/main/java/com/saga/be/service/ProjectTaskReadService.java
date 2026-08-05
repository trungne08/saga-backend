package com.saga.be.service;

import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectTaskReadService {

    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "externalKey", "externalKey",
            "title", "title",
            "status", "status",
            "priority", "priority",
            "storyPoint", "storyPoint",
            "dueDate", "dueDate",
            "externalUpdatedAt", "externalUpdatedAt"
    );

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public Page<TaskReadResponse> getTasks(
            SagaPrincipal principal,
            UUID projectId,
            String keyword,
            UUID sprintId,
            UUID assigneeId,
            TaskStatus status,
            String sortBy,
            String sortDirection,
            int page,
            int size
    ) {
        validatePage(page, size);
        Project project = requireProjectAccess(principal, projectId);
        Specification<Task> specification = projectSpecification(project.getId());

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("externalKey")), pattern),
                    builder.like(builder.lower(root.get("title")), pattern)
            ));
        }
        if (sprintId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("sprint").get("id"), sprintId));
        }
        if (assigneeId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("assignee").get("id"), assigneeId));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }

        return taskRepository.findAll(
                specification,
                PageRequest.of(page, size, taskSort(sortBy, sortDirection))
        ).map(TaskReadResponse::from);
    }

    @Transactional(readOnly = true)
    public TaskReadResponse getTask(SagaPrincipal principal, UUID projectId, UUID taskId) {
        requireProjectAccess(principal, projectId);
        return taskRepository.findByIdAndProjectId(taskId, projectId)
                .map(TaskReadResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private Project requireProjectAccess(SagaPrincipal principal, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (principal != null && principal.applicationRole() == ApplicationRole.ADMIN) {
            return project;
        }
        if (principal != null
                && principal.applicationRole() == ApplicationRole.LECTURER
                && project.getCourse() != null
                && project.getCourse().getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        project.getCourse().getInstructor().getId()
                )) {
            return project;
        }
        if (principal != null
                && principal.applicationRole() == ApplicationRole.STUDENT
                && principal.localProfileId() != null) {
            Team owningTeam = teamRepository.findByProjectId(projectId).orElse(null);
            if (owningTeam != null && teamMemberRepository.existsByTeamIdAndStudentId(
                    owningTeam.getId(),
                    principal.localProfileId()
            )) {
                return project;
            }
        }
        throw new AccessDeniedException("You do not have access to this project's tasks");
    }

    private Specification<Task> projectSpecification(UUID projectId) {
        return (root, query, builder) -> builder.equal(root.get("project").get("id"), projectId);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100"
            );
        }
    }

    private Sort taskSort(String sortBy, String sortDirection) {
        String property = SORT_PROPERTIES.get(sortBy);
        if (property == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported task sort field");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task sort direction");
        }
        return Sort.by(
                new Sort.Order(direction, property),
                new Sort.Order(direction, "id")
        );
    }
}
