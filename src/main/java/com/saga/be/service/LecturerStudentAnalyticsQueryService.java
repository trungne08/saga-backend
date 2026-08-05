package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Document;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LecturerStudentAnalyticsQueryService {
    private final LecturerAnalyticsAuthorizationService authorization;
    private final TaskRepository taskRepository;
    private final CommitDataRepository commitDataRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.StudentProgress progress(SagaPrincipal principal, UUID courseId, UUID studentId) {
        TeamMember membership = authorization.requireStudentInCourse(principal, courseId, studentId);
        Team team = membership.getTeam();
        if (team.getProject() == null) {
            return new LecturerAnalyticsResponses.StudentProgress(courseId, studentId, team.getId(), null,
                    0, 0, 0.0, 0, Map.of(), 0);
        }
        UUID projectId = team.getProject().getId();
        List<Task> tasks = taskRepository.findByProjectIdAndAssigneeId(projectId, studentId);
        long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        Map<TaskType, Long> distribution = new EnumMap<>(TaskType.class);
        tasks.stream().filter(task -> task.getType() != null)
                .forEach(task -> distribution.merge(task.getType(), 1L, Long::sum));
        return new LecturerAnalyticsResponses.StudentProgress(courseId, studentId, team.getId(), projectId,
                tasks.size(), completed, tasks.isEmpty() ? 0.0 : completed * 100.0 / tasks.size(),
                commitDataRepository.countByProjectIdAndAuthorId(projectId, studentId), Map.copyOf(distribution),
                tasks.stream().filter(task -> task.getType() == null).count());
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.StudentActivities activities(SagaPrincipal principal, UUID courseId,
            UUID studentId, Pageable pageable) {
        TeamMember membership = authorization.requireStudentInCourse(principal, courseId, studentId);
        if (membership.getTeam().getProject() == null) {
            return new LecturerAnalyticsResponses.StudentActivities(courseId, studentId, Page.empty(pageable));
        }
        UUID projectId = membership.getTeam().getProject().getId();
        List<LecturerAnalyticsResponses.Activity> result = new ArrayList<>();
        int candidateLimit = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                pageable.getOffset() + pageable.getPageSize()));
        PageRequest candidates = PageRequest.of(0, Math.max(1, candidateLimit));
        for (CommitData commit : commitDataRepository
                .findByAuthorIdAndRepoProjectIdOrderByTimestampDescIdDesc(studentId, projectId, candidates)) {
            if (commit.getTimestamp() != null) {
                result.add(new LecturerAnalyticsResponses.Activity(commit.getId(), "COMMIT", commit.getTimestamp(),
                        commit.getMessage() == null ? "Commit" : commit.getMessage(), projectId,
                        commit.getTask() != null && commit.getTask().getSprint() != null
                                ? commit.getTask().getSprint().getId() : null));
            }
        }
        for (Document document : documentRepository
                .findByProjectIdAndAuthorIdOrderByCreatedAtDescIdDesc(projectId, studentId, candidates)) {
            if (document.getCreatedAt() != null) {
                result.add(new LecturerAnalyticsResponses.Activity(document.getId(), "DOCUMENT", document.getCreatedAt(),
                        document.getTitle() == null ? "Document" : document.getTitle(), projectId, null));
            }
        }
        result.sort(Comparator.comparing(LecturerAnalyticsResponses.Activity::occurredAt).reversed()
                .thenComparing(LecturerAnalyticsResponses.Activity::type)
                .thenComparing(LecturerAnalyticsResponses.Activity::sourceId));
        int from = Math.min((int) pageable.getOffset(), result.size());
        int to = Math.min(from + pageable.getPageSize(), result.size());
        long totalElements = commitDataRepository.countActivitiesByProjectIdAndAuthorId(projectId, studentId)
                + documentRepository.countByProjectIdAndAuthorIdAndCreatedAtIsNotNull(projectId, studentId);
        return new LecturerAnalyticsResponses.StudentActivities(courseId, studentId,
                new PageImpl<>(result.subList(from, to), pageable, totalElements));
    }
}
