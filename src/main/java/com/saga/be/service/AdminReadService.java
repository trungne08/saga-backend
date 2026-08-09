package com.saga.be.service;

import com.saga.be.dto.response.AdminAuditLogResponse;
import com.saga.be.dto.response.AdminProjectReadResponse;
import com.saga.be.dto.response.AdminSystemStatsResponse;
import com.saga.be.dto.response.AdminTeamReadResponse;
import com.saga.be.dto.response.AdminUserReadResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.AdminUserReadRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SystemAuditLogRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only administrator views backed exclusively by local MySQL/Mongo snapshots. */
@Service
@RequiredArgsConstructor
public class AdminReadService {

    private final AdminUserReadRepository adminUserReadRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;
    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final JiraBoardRepository jiraBoardRepository;
    private final GitRepoRepository gitRepoRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserReadResponse> users(String keyword, ApplicationRole role,
            AccountStatus accountStatus, int page, int size) {
        return adminUserReadRepository.findAll(keyword, role, accountStatus, pageRequest(page, size));
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> auditLogs(int page, int size) {
        validatePage(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return systemAuditLogRepository.findAll(pageable).map(log -> new AdminAuditLogResponse(
                log.getId(), log.getAction(), log.getTargetEntity(), log.getTimestamp()));
    }

    @Transactional(readOnly = true)
    public AdminSystemStatsResponse systemStats() {
        long profiles = adminRepository.count() + lecturerRepository.count() + studentRepository.count();
        return new AdminSystemStatsResponse(profiles, courseRepository.count(), teamRepository.count(),
                projectRepository.count(), jiraBoardRepository.countByConnectionStatus(IntegrationStatus.ACTIVE),
                gitRepoRepository.countByConnectionStatus(IntegrationStatus.ACTIVE), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public Page<AdminTeamReadResponse> teams(int page, int size) {
        return teamRepository.findAll(pageRequest(page, size)).map(this::teamResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminProjectReadResponse> projects(int page, int size) {
        Page<Project> projects = projectRepository.findAll(pageRequest(page, size));
        List<UUID> projectIds = projects.getContent().stream().map(Project::getId).toList();
        Map<UUID, JiraBoard> jiraByProject = jiraBoardRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(board -> board.getProject().getId(), Function.identity()));
        Map<UUID, List<GitRepo>> reposByProject = gitRepoRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(repo -> repo.getProject().getId()));
        return projects.map(project -> projectResponse(project, jiraByProject.get(project.getId()),
                reposByProject.getOrDefault(project.getId(), List.of())));
    }

    private PageRequest pageRequest(int page, int size) {
        validatePage(page, size);
        return PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100");
        }
    }

    private AdminTeamReadResponse teamResponse(Team team) {
        Project project = team.getProject();
        return new AdminTeamReadResponse(team.getId(), team.getName(), courseSummary(team.getCourse()),
                project == null ? null : new AdminTeamReadResponse.ProjectSummary(project.getId(), project.getName()));
    }

    private AdminProjectReadResponse projectResponse(Project project, JiraBoard jiraBoard,
            Collection<GitRepo> repositories) {
        long activeRepositories = repositories.stream()
                .filter(repository -> repository.getConnectionStatus() == IntegrationStatus.ACTIVE).count();
        return new AdminProjectReadResponse(project.getId(), project.getName(), project.getDescription(),
                projectCourseSummary(project.getCourse()),
                jiraBoard == null ? null : new AdminProjectReadResponse.JiraSummary(jiraBoard.getConnectionStatus()),
                new AdminProjectReadResponse.GitHubSummary(repositories.size(), activeRepositories));
    }

    private AdminTeamReadResponse.CourseSummary courseSummary(Course course) {
        return course == null ? null : new AdminTeamReadResponse.CourseSummary(
                course.getId(), course.getCourseCode(), course.getName());
    }

    private AdminProjectReadResponse.CourseSummary projectCourseSummary(Course course) {
        return course == null ? null : new AdminProjectReadResponse.CourseSummary(
                course.getId(), course.getCourseCode(), course.getName());
    }
}
