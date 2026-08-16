package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.AgentConversationCreateRequest;
import com.saga.be.dto.request.AgentMessageSendRequest;
import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.Course;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class AgentGatewayServiceTest {

    @Test
    void confirmCreateCallsExistingJiraServiceExactlyOnceWithStableIdempotency() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID actionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.claimAction(actor, actionId)).thenReturn(action(
                actionId,
                "TASK_CREATE",
                Map.of(
                        "projectId", projectId.toString(),
                        "title", "Fix login",
                        "type", "BUG",
                        "priority", "HIGH"
                )
        ));

        service.confirm(actor, actionId);

        ArgumentCaptor<JiraTaskCreateRequest> request = ArgumentCaptor.forClass(JiraTaskCreateRequest.class);
        verify(writes).create(eq(actor), eq(projectId), eq("saga-agent-stable"), request.capture());
        assertEquals("Fix login", request.getValue().title());
        assertEquals(TaskType.BUG, request.getValue().type());
        assertEquals(Priority.HIGH, request.getValue().priority());
        assertNull(request.getValue().issueTypeId());
        assertNull(request.getValue().priorityId());
        verify(ai).finalizeAction(actor, actionId, true, null);
    }

    @Test
    void confirmSparseUpdateCannotSmuggleDedicatedOperationFields() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.LECTURER);
        UUID actionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(ai.claimAction(actor, actionId)).thenReturn(action(
                actionId,
                "TASK_UPDATE",
                Map.of(
                        "projectId", projectId.toString(),
                        "taskId", taskId.toString(),
                        "title", "Updated title",
                        "type", "REQUEST"
                )
        ));

        service.confirm(actor, actionId);

        ArgumentCaptor<JiraTaskUpdateRequest> request = ArgumentCaptor.forClass(JiraTaskUpdateRequest.class);
        verify(writes).update(eq(actor), eq(projectId), eq(taskId), eq("saga-agent-stable"), request.capture());
        assertEquals("Updated title", request.getValue().title());
        assertEquals(TaskType.REQUEST, request.getValue().type());
        assertNull(request.getValue().priorityId());
    }

    @Test
    void rejectNeverCallsJiraWriteService() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.ADMIN);
        UUID actionId = UUID.randomUUID();
        when(ai.rejectAction(actor, actionId)).thenReturn(action(actionId, "TASK_CREATE", null));

        service.reject(actor, actionId);

        verifyNoInteractions(writes);
        verify(ai, never()).claimAction(any(), any());
    }

    @Test
    void artifactDownloadReauthorizesProjectBeforeFetchingContent() {
        AgentAiClient ai = mock(AgentAiClient.class);
        ProjectDetailService projects = mock(ProjectDetailService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), projects,
                mock(com.saga.be.repository.StudentRepository.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(com.saga.be.repository.TeamMemberRepository.class),
                mock(AgentConversationScopeService.class)
        );
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID artifactId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.artifact(actor, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "SRS_DOCX", "PROJECT",
                projectId.toString(), "SRS-project.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(ai.artifactContent(actor, artifactId)).thenReturn(new byte[]{1, 2, 3});

        AgentGatewayService.DownloadedArtifact result = service.download(actor, artifactId);

        verify(projects).get(actor, projectId);
        verify(ai).artifactContent(actor, artifactId);
        assertEquals("SRS-project.docx", result.filename());
    }

    @Test
    void unsafeArtifactFilenameIsRejectedBeforeContentFetch() {
        AgentAiClient ai = mock(AgentAiClient.class);
        ProjectDetailService projects = mock(ProjectDetailService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), projects,
                mock(com.saga.be.repository.StudentRepository.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(com.saga.be.repository.TeamMemberRepository.class),
                mock(AgentConversationScopeService.class)
        );
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID artifactId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.artifact(actor, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "SRS_DOCX", "PROJECT",
                projectId.toString(), "../secret.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));

        assertThrows(IntegrationException.class, () -> service.download(actor, artifactId));

        verify(projects).get(actor, projectId);
        verify(ai, never()).artifactContent(any(), any());
    }

    @Test
    void lecturerReportDownloadReauthorizesCurrentCourseAccess() {
        AgentAiClient ai = mock(AgentAiClient.class);
        LecturerAnalyticsAuthorizationService authorization = mock(LecturerAnalyticsAuthorizationService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), authorization,
                mock(com.saga.be.repository.TeamMemberRepository.class),
                mock(AgentConversationScopeService.class)
        );
        UUID artifactId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        Course course = Course.builder().courseCode("PRN231").name("PRN").build();
        course.setId(courseId);
        when(ai.artifact(lecturer, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LECTURER_PROGRESS_REPORT", "COURSE",
                courseId.toString(), "course-progress.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(authorization.requireCourseAccess(lecturer, courseId)).thenReturn(course);
        when(ai.artifactContent(lecturer, artifactId)).thenReturn(new byte[]{1, 2, 3});

        assertEquals("course-progress.docx", service.download(lecturer, artifactId).filename());

        SagaPrincipal admin = actor(ApplicationRole.ADMIN);
        when(ai.artifact(admin, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LECTURER_PROGRESS_REPORT", "COURSE",
                courseId.toString(), "course-progress.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(authorization.requireCourseAccess(admin, courseId)).thenReturn(course);
        when(ai.artifactContent(admin, artifactId)).thenReturn(new byte[]{1, 2, 3});
        assertEquals("course-progress.docx", service.download(admin, artifactId).filename());

        UUID otherLecturerId = UUID.randomUUID();
        SagaPrincipal otherLecturer = new SagaPrincipal(
                "other-lecturer", "other@example.test", "Other",
                ApplicationRole.LECTURER, otherLecturerId, AccountStatus.ACTIVE
        );
        when(ai.artifact(otherLecturer, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LECTURER_PROGRESS_REPORT", "COURSE",
                courseId.toString(), "course-progress.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(authorization.requireCourseAccess(otherLecturer, courseId))
                .thenThrow(new AccessDeniedException("denied"));
        assertThrows(IntegrationException.class, () -> service.download(otherLecturer, artifactId));
        verify(ai, never()).artifactContent(eq(otherLecturer), any());

        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        when(ai.artifact(student, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LECTURER_PROGRESS_REPORT", "COURSE",
                courseId.toString(), "course-progress.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(authorization.requireCourseAccess(student, courseId))
                .thenThrow(new AccessDeniedException("denied"));
        assertThrows(IntegrationException.class, () -> service.download(student, artifactId));
        verify(ai, never()).artifactContent(eq(student), any());
    }

    @Test
    void adminReportDownloadIsAdminOnly() {
        AgentAiClient ai = mock(AgentAiClient.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), mock(LecturerAnalyticsAuthorizationService.class),
                mock(com.saga.be.repository.TeamMemberRepository.class),
                mock(AgentConversationScopeService.class)
        );
        UUID artifactId = UUID.randomUUID();
        SagaPrincipal admin = actor(ApplicationRole.ADMIN);
        when(ai.artifact(admin, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "ADMIN_SYSTEM_REPORT", "SYSTEM",
                "SYSTEM", "admin-system.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(ai.artifactContent(admin, artifactId)).thenReturn(new byte[]{9});
        assertEquals("admin-system.docx", service.download(admin, artifactId).filename());

        SagaPrincipal lecturer = actor(ApplicationRole.LECTURER);
        when(ai.artifact(lecturer, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "ADMIN_SYSTEM_REPORT", "SYSTEM",
                "SYSTEM", "admin-system.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        assertThrows(IntegrationException.class, () -> service.download(lecturer, artifactId));
    }

    @Test
    void leaderTeamReportDownloadRequiresExactCurrentLeader() {
        AgentAiClient ai = mock(AgentAiClient.class);
        TeamMemberRepository teamMembers = mock(TeamMemberRepository.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), mock(LecturerAnalyticsAuthorizationService.class),
                teamMembers, mock(AgentConversationScopeService.class)
        );
        UUID artifactId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        SagaPrincipal leader = actor(ApplicationRole.STUDENT);
        when(ai.artifact(leader, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LEADER_TEAM_PROGRESS_REPORT", "TEAM",
                teamId.toString(), "Leader-Team-Progress-Report-team.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(teamMembers.existsByTeamIdAndStudentIdAndRoleInTeam(
                teamId, leader.localProfileId(), RoleInTeam.LEADER
        )).thenReturn(true);
        when(ai.artifactContent(leader, artifactId)).thenReturn(new byte[]{7});
        assertEquals("Leader-Team-Progress-Report-team.docx", service.download(leader, artifactId).filename());

        SagaPrincipal member = actor(ApplicationRole.STUDENT);
        when(ai.artifact(member, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "LEADER_TEAM_PROGRESS_REPORT", "TEAM",
                teamId.toString(), "Leader-Team-Progress-Report-team.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(teamMembers.existsByTeamIdAndStudentIdAndRoleInTeam(
                teamId, member.localProfileId(), RoleInTeam.LEADER
        )).thenReturn(false);
        assertThrows(IntegrationException.class, () -> service.download(member, artifactId));
        verify(ai, never()).artifactContent(eq(member), any());
    }

    @Test
    void createConversationValidatesCourseScopeAndDoesNotAcceptActorIdentity() {
        AgentAiClient ai = mock(AgentAiClient.class);
        AgentConversationScopeService scopes = mock(AgentConversationScopeService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), mock(LecturerAnalyticsAuthorizationService.class),
                mock(TeamMemberRepository.class), scopes
        );
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        UUID courseId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(ai.createConversation(student, "Course A chat", null, courseId)).thenReturn(
                new AgentApiResponses.Conversation(
                        conversationId, "Course A chat", null, "STUDENT", false,
                        "2026-08-16T00:00:00Z", "2026-08-16T00:00:00Z", java.util.List.of()
                )
        );

        AgentApiResponses.Conversation created = service.create(
                student, new AgentConversationCreateRequest("Course A chat", courseId)
        );

        verify(scopes).requireAccessibleCourse(student, courseId);
        verify(scopes).bindOnCreate(student, conversationId, courseId);
        assertEquals(courseId, created.courseId());
        verify(ai, never()).createConversation(eq(student), any(), any(), eq(student.localProfileId()));
    }

    @Test
    void sendRejectsConversationBoundToAnotherCourseAndKeepsActorFromSession() {
        AgentAiClient ai = mock(AgentAiClient.class);
        AgentDelegationService delegations = mock(AgentDelegationService.class);
        AgentConversationScopeService scopes = mock(AgentConversationScopeService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, delegations, mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), mock(LecturerAnalyticsAuthorizationService.class),
                mock(TeamMemberRepository.class), scopes
        );
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        UUID conversationId = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        when(scopes.resolveForMessage(student, conversationId, courseB))
                .thenThrow(IntegrationException.conflict(
                        "AI_AGENT_COURSE_SCOPE_MISMATCH",
                        "This conversation is bound to a different Course. Start a new conversation in the Course you want to use."
                ));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.send(student, conversationId, new AgentMessageSendRequest("project của tôi", courseB))
        );

        assertEquals("AI_AGENT_COURSE_SCOPE_MISMATCH", failure.getCode());
        verifyNoInteractions(ai);
        verify(delegations, never()).issue(any(), any(), any());
    }

    @Test
    void sendPropagatesValidatedCourseIntoDelegationAndAiClient() {
        AgentAiClient ai = mock(AgentAiClient.class);
        AgentDelegationService delegations = mock(AgentDelegationService.class);
        AgentConversationScopeService scopes = mock(AgentConversationScopeService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, delegations, mock(JiraTaskWriteService.class), mock(ProjectDetailService.class),
                mock(StudentRepository.class), mock(LecturerAnalyticsAuthorizationService.class),
                mock(TeamMemberRepository.class), scopes
        );
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        UUID conversationId = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        when(scopes.resolveForMessage(student, conversationId, courseA)).thenReturn(courseA);
        when(delegations.issue(student, conversationId, courseA)).thenReturn("opaque-context");
        when(ai.sendMessage(student, conversationId, "opaque-context", "tôi còn task nào", null, courseA))
                .thenReturn(new AgentApiResponses.Chat(
                        conversationId, UUID.randomUUID(), "ok", "COMPLETED",
                        java.util.List.of(), null, null, null, java.util.List.of(), "FAKE", "test"
                ));

        service.send(student, conversationId, new AgentMessageSendRequest("tôi còn task nào", courseA));

        verify(delegations).issue(student, conversationId, courseA);
        verify(ai).sendMessage(student, conversationId, "opaque-context", "tôi còn task nào", null, courseA);
    }

    private AgentGatewayService service(AgentAiClient ai, JiraTaskWriteService writes) {
        return new AgentGatewayService(
                ai,
                mock(AgentDelegationService.class),
                writes,
                mock(ProjectDetailService.class),
                mock(StudentRepository.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(com.saga.be.repository.TeamMemberRepository.class),
                mock(AgentConversationScopeService.class)
        );
    }

    private AgentApiResponses.PendingAction action(
            UUID id, String type, Map<String, Object> payload
    ) {
        return new AgentApiResponses.PendingAction(
                id, UUID.randomUUID().toString(), type, "EXECUTING", "summary",
                "saga-agent-stable", "2026-08-14T12:00:00Z", null, payload
        );
    }

    private SagaPrincipal actor(ApplicationRole role) {
        return new SagaPrincipal(
                role.name().toLowerCase() + "-sub",
                role.name().toLowerCase() + "@example.test",
                role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
