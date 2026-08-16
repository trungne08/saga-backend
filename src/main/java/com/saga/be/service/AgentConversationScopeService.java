package com.saga.be.service;

import com.saga.be.entity.AiAgentConversationScope;
import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AiAgentConversationScopeRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentConversationScopeService {

    private final AiAgentConversationScopeRepository scopes;
    private final CourseRepository courses;
    private final TeamMemberRepository teamMembers;
    private final ProjectRepository projects;
    private final TeamRepository teams;

    public AgentConversationScopeService(
            AiAgentConversationScopeRepository scopes,
            CourseRepository courses,
            TeamMemberRepository teamMembers,
            ProjectRepository projects,
            TeamRepository teams
    ) {
        this.scopes = scopes;
        this.courses = courses;
        this.teamMembers = teamMembers;
        this.projects = projects;
        this.teams = teams;
    }

    @Transactional(readOnly = true)
    public Course requireAccessibleCourse(SagaPrincipal actor, UUID courseId) {
        if (actor == null || actor.localProfileId() == null || actor.applicationRole() == null
                || courseId == null) {
            throw IntegrationException.forbidden("An authenticated local profile is required");
        }
        Course course = courses.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new IntegrationException(
                        HttpStatus.NOT_FOUND,
                        "AI_AGENT_COURSE_NOT_FOUND",
                        "The requested Course is not available"
                ));
        if (actor.applicationRole() == ApplicationRole.ADMIN) {
            return course;
        }
        if (actor.applicationRole() == ApplicationRole.LECTURER) {
            if (course.getInstructor() == null
                    || !actor.localProfileId().equals(course.getInstructor().getId())) {
                throw courseForbidden();
            }
            return course;
        }
        if (actor.applicationRole() == ApplicationRole.STUDENT) {
            if (!teamMembers.existsByStudentIdAndTeamCourseId(actor.localProfileId(), courseId)) {
                throw courseForbidden();
            }
            return course;
        }
        throw courseForbidden();
    }

    @Transactional
    public void bindOnCreate(SagaPrincipal actor, UUID conversationId, UUID courseId) {
        if (conversationId == null || courseId == null) {
            return;
        }
        requireAccessibleCourse(actor, courseId);
        persist(actor, conversationId, courseId);
    }

    @Transactional
    public UUID resolveForMessage(SagaPrincipal actor, UUID conversationId, UUID requestedCourseId) {
        if (conversationId == null) {
            throw IntegrationException.invalid(
                    "AI_AGENT_CONVERSATION_INVALID", "A conversation is required"
            );
        }
        Optional<AiAgentConversationScope> existing = scopes.findByConversationId(conversationId);
        if (existing.isPresent()) {
            UUID bound = existing.get().getCourseId();
            if (requestedCourseId != null && !bound.equals(requestedCourseId)) {
                throw IntegrationException.conflict(
                        "AI_AGENT_COURSE_SCOPE_MISMATCH",
                        "This conversation is bound to a different Course. Start a new conversation in the Course you want to use."
                );
            }
            requireAccessibleCourse(actor, bound);
            requireOwner(actor, existing.get());
            return bound;
        }
        if (requestedCourseId == null) {
            return null;
        }
        requireAccessibleCourse(actor, requestedCourseId);
        persist(actor, conversationId, requestedCourseId);
        return requestedCourseId;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> courseIdFor(UUID conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return scopes.findByConversationId(conversationId).map(AiAgentConversationScope::getCourseId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> courseIdsFor(Collection<UUID> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        return scopes.findByConversationIdIn(conversationIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        AiAgentConversationScope::getConversationId,
                        AiAgentConversationScope::getCourseId
                ));
    }

    @Transactional(readOnly = true)
    public UUID effectiveCourseId(UUID delegatedCourseId, UUID requestedCourseId) {
        if (delegatedCourseId != null && requestedCourseId != null
                && !delegatedCourseId.equals(requestedCourseId)) {
            throw IntegrationException.conflict(
                    "AI_AGENT_COURSE_SCOPE_MISMATCH",
                    "The requested Course is outside the active Course conversation scope"
            );
        }
        return delegatedCourseId != null ? delegatedCourseId : requestedCourseId;
    }

    @Transactional(readOnly = true)
    public void requireProjectInScope(UUID courseId, UUID projectId) {
        if (courseId == null || projectId == null) {
            return;
        }
        Project project = projects.findWithCourseAndInstructorById(projectId).orElse(null);
        if (project == null || project.getCourse() == null
                || !courseId.equals(project.getCourse().getId())) {
            throw resourceOutsideCourse();
        }
    }

    @Transactional(readOnly = true)
    public void requireTeamInScope(UUID courseId, UUID teamId) {
        if (courseId == null || teamId == null) {
            return;
        }
        Team team = teams.findWithCourseAndInstructorById(teamId).orElse(null);
        if (team == null || team.getCourse() == null
                || !courseId.equals(team.getCourse().getId())) {
            throw resourceOutsideCourse();
        }
    }

    private void persist(SagaPrincipal actor, UUID conversationId, UUID courseId) {
        AiAgentConversationScope scope = new AiAgentConversationScope();
        scope.setConversationId(conversationId);
        scope.setCourseId(courseId);
        scope.setOwnerProfileId(actor.localProfileId());
        scope.setOwnerApplicationRole(actor.applicationRole());
        scopes.saveAndFlush(scope);
    }

    private void requireOwner(SagaPrincipal actor, AiAgentConversationScope scope) {
        if (!actor.localProfileId().equals(scope.getOwnerProfileId())
                || actor.applicationRole() != scope.getOwnerApplicationRole()) {
            throw courseForbidden();
        }
    }

    private IntegrationException courseForbidden() {
        return new IntegrationException(
                HttpStatus.FORBIDDEN,
                "AI_AGENT_COURSE_FORBIDDEN",
                "The current actor cannot use this Course as AI chat scope"
        );
    }

    private IntegrationException resourceOutsideCourse() {
        return new IntegrationException(
                HttpStatus.FORBIDDEN,
                "AI_AGENT_RESOURCE_OUTSIDE_COURSE_SCOPE",
                "The requested resource does not belong to the active Course"
        );
    }
}
