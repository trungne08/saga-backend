package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraCreateField;
import com.saga.be.integration.provider.JiraCreateFieldAllowedValue;
import com.saga.be.integration.provider.JiraCreateIssueType;
import com.saga.be.integration.provider.JiraIssueReference;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.integration.write.JiraCanonicalTaskReadService;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.dto.response.TaskReadResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

class JiraTaskWriteServiceTest {

    @Test
    void createsRemotelyThenFetchesAndUpsertsCanonicalSnapshot() {
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraIssueUpsertService upserts = mock(JiraIssueUpsertService.class);
        JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        JiraCanonicalTaskReadService canonicalReads = mock(JiraCanonicalTaskReadService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").jiraProjectId("10000")
                .connectionStatus(IntegrationStatus.ACTIVE).grantedScopes("write:jira-work").build(); board.setId(boardId);
        JiraWriteOperation operation = JiraWriteOperation.builder().project(project).status(JiraWriteOperationStatus.PENDING).build(); operation.setId(operationId);
        Task task = Task.builder().project(project).externalId("101").externalKey("P-1").title("Task").build(); task.setId(UUID.randomUUID());
        JiraTaskCreateRequest request = new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null);
        SagaPrincipal principal = new SagaPrincipal("sub", "a@b.test", "User", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE);
        when(authorization.requireProjectManager(principal, projectId)).thenReturn(project);
        when(operations.fingerprint(any())).thenReturn("fingerprint");
        when(operations.claim(eq(project), eq(principal), eq(JiraWriteOperationType.TASK_CREATE), any(), eq("fingerprint"))).thenReturn(operation);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(new JiraCreateIssueType("3", "Task", false, null)));
        when(provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of())
        ));
        when(provider.createIssue(eq("token"), eq("cloud"), any())).thenReturn(new JiraIssueReference("101", "P-1"));
        JiraIssueSnapshot canonical = snapshot();
        when(provider.getIssue("token", "cloud", "101")).thenReturn(canonical);
        when(canonicalReads.findResponse(projectId, "101")).thenReturn(Optional.of(TaskReadResponse.from(task)));

        assertEquals(task.getId(), new JiraTaskWriteService(authorization, boards, credentials, provider, upserts, operations, canonicalReads, tasks,
                mock(IdentityMapRepository.class), mock(SprintRepository.class), mock(JiraSprintUpsertService.class)).create(principal, projectId, "key", request).id());

        InOrder ordered = inOrder(authorization, provider, upserts);
        ordered.verify(authorization).requireProjectManager(principal, projectId);
        ordered.verify(provider).createIssue(eq("token"), eq("cloud"), any());
        ordered.verify(provider).getIssue("token", "cloud", "101");
        ordered.verify(upserts).upsert(boardId, canonical);
        verify(operations).markRemoteSucceeded(operationId, "101", "P-1");
        verify(operations).complete(operationId);
    }

    @Test
    void rejectsMetadataDisallowedOptionalFieldBeforeRemoteCreate() {
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        UUID projectId = UUID.randomUUID(); Project project = Project.builder().build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").jiraProjectId("10000")
                .connectionStatus(IntegrationStatus.ACTIVE).grantedScopes("write:jira-work").build();
        JiraWriteOperation operation = JiraWriteOperation.builder().status(JiraWriteOperationStatus.PENDING).build(); operation.setId(UUID.randomUUID());
        SagaPrincipal principal = new SagaPrincipal("sub", "a", "u", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE);
        when(authorization.requireProjectManager(principal, projectId)).thenReturn(project);
        when(operations.fingerprint(any())).thenReturn("f"); when(operations.claim(any(), any(), any(), any(), any())).thenReturn(operation);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board)); when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(new JiraCreateIssueType("3", "Task", false, null)));
        when(provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(new JiraCreateField("summary", "Summary", true, "string", null, List.of()), new JiraCreateField("issuetype", "Type", true, "string", null, List.of())));

        assertEquals("JIRA_CREATE_FIELD_NOT_ALLOWED", assertThrows(IntegrationException.class, () -> new JiraTaskWriteService(authorization, boards, credentials, provider,
                mock(JiraIssueUpsertService.class), operations, mock(JiraCanonicalTaskReadService.class), mock(TaskRepository.class), mock(IdentityMapRepository.class), mock(SprintRepository.class), mock(JiraSprintUpsertService.class)).create(principal, projectId, "key", new JiraTaskCreateRequest("Task", "3", "desc", null, null, null, null, null))).getCode());
        verify(provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void rejectsStaleExplicitIssueTypeBeforeMetadataFields() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "stale-type"))
                .thenThrow(IntegrationException.conflict(
                        "JIRA_RESOURCE_NOT_FOUND", "The selected Jira resource is no longer accessible"));

        String logged = captureCreateFailure(() -> assertEquals("JIRA_ISSUE_TYPE_INVALID", assertThrows(
                IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "stale-type", null, null, null, null, null, null))
        ).getCode()));

        verify(fixture.operations).failed(fixture.operation.getId(), "JIRA_ISSUE_TYPE_INVALID");
        verify(fixture.provider, never()).getCreateFields("token", "cloud", "10000", "stale-type");
        verify(fixture.provider, never()).createIssue(any(), any(), any());
        assertTrue(logged.contains("operation=TASK_CREATE"));
        assertTrue(logged.contains("stage=ISSUE_TYPE_RESOLUTION"));
        assertTrue(logged.contains("resourceType=ISSUE_TYPE"));
        assertTrue(logged.contains("resolutionMode=EXPLICIT"));
        assertTrue(logged.contains("resolutionResult=INVALID"));
        assertTrue(logged.contains("upstreamHttpStatus=NONE"));
        assertTrue(logged.contains("errorCategory=JIRA_ISSUE_TYPE_INVALID"));
        assertTrue(logged.contains("writeOperationStatus=FAILED"));
    }

    @Test
    void rejectsExplicitPriorityOutsideMetadataAllowedValuesBeforeProviderCreate() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null,
                        List.of(new JiraCreateFieldAllowedValue("1", null, "High")))
        ));
        String logged = captureCreateFailure(() -> assertEquals("JIRA_PRIORITY_INVALID", assertThrows(
                IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, "999", null, null, null, null))
        ).getCode()));

        verify(fixture.provider, never()).createIssue(any(), any(), any());
        verify(fixture.operations).failed(fixture.operation.getId(), "JIRA_PRIORITY_INVALID");
        assertTrue(logged.contains("stage=PRIORITY_RESOLUTION"));
        assertTrue(logged.contains("resourceType=PRIORITY"));
        assertTrue(logged.contains("resolutionMode=EXPLICIT"));
        assertTrue(logged.contains("resolutionResult=INVALID"));
    }

    @Test
    void usesValidatedExplicitPriorityIdForBackwardCompatibleRequest() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null,
                        List.of(new JiraCreateFieldAllowedValue("1", null, "High")))
        ));
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any())).thenThrow(
                IntegrationException.conflict("JIRA_RESOURCE_NOT_FOUND", "The selected Jira resource is no longer accessible"));

        assertEquals("JIRA_RESOURCE_NOT_FOUND", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, "1", null, null, null, null))).getCode());

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "1").equals(fields.get("priority"))));
    }

    @Test
    void keepsFieldNotAllowedWhenBusinessPriorityIsRequestedButUnavailable() {
        CreateFixture fixture = fixture();

        assertEquals("JIRA_CREATE_FIELD_NOT_ALLOWED", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.HIGH, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void autoResolvesBusinessTypeAndPriorityToProjectMetadataIds() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null,
                        List.of(new JiraCreateFieldAllowedValue("1", null, "High")))
        ));
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any()))
                .thenReturn(new JiraIssueReference("101", "P-1"));
        JiraIssueSnapshot canonical = snapshot();
        when(fixture.provider.getIssue("token", "cloud", "101")).thenReturn(canonical);
        Task task = Task.builder().project(fixture.project).externalId("101").externalKey("P-1").title("Task").build();
        task.setId(UUID.randomUUID());
        when(fixture.canonicalReads.findResponse(fixture.projectId, "101"))
                .thenReturn(Optional.of(TaskReadResponse.from(task)));

        assertEquals(task.getId(), fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.HIGH, null,
                        null, null, null, null, null)).id());

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "3").equals(fields.get("issuetype"))
                        && Map.of("id", "1").equals(fields.get("priority"))));
        verify(fixture.operations).markRemoteSucceeded(fixture.operation.getId(), "101", "P-1");
        verify(fixture.operations).complete(fixture.operation.getId());
    }

    @Test
    void omitsPriorityWhenNeitherBusinessPriorityNorOverrideIsRequested() {
        CreateFixture fixture = fixture();
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                !fields.containsKey("priority")));
    }

    @Test
    void failsClosedWhenAutoIssueTypeHasNoCandidate() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000"))
                .thenReturn(List.of(new JiraCreateIssueType("1", "Bug", false, null)));

        assertEquals("JIRA_ISSUE_TYPE_RESOLUTION_NOT_FOUND", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.EPIC, null, null, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).getCreateFields(any(), any(), any(), any());
        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void prefersExactTaskNameOverSpikeSemanticFallback() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(
                new JiraCreateIssueType("3", "Task", false, null),
                new JiraCreateIssueType("4", "Spike", false, null)
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "3").equals(fields.get("issuetype"))));
    }

    @Test
    void deduplicatesSameProviderIdBeforeAutoIssueTypeResolution() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(
                new JiraCreateIssueType("3", "Task", false, null),
                new JiraCreateIssueType("3", "Task", false, null)
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "3").equals(fields.get("issuetype"))));
    }

    @Test
    void usesSingleSpikeSemanticFallbackWhenNoExactTaskExists() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000"))
                .thenReturn(List.of(new JiraCreateIssueType("4", "Spike", false, null)));
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "4")).thenReturn(requiredFields());
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "4").equals(fields.get("issuetype"))));
    }

    @Test
    void failsClosedWhenMultipleExactTaskNamesHaveDistinctProviderIds() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(
                new JiraCreateIssueType("3", "Task", false, null),
                new JiraCreateIssueType("4", "Task", false, null)
        ));

        assertEquals("JIRA_ISSUE_TYPE_RESOLUTION_AMBIGUOUS", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).getCreateFields(any(), any(), any(), any());
        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void failsClosedWhenMultipleIssueTypeFallbacksRemainWithoutExactName() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(
                new JiraCreateIssueType("3", "Spike", false, null),
                new JiraCreateIssueType("4", "Technical Task", false, null)
        ));

        assertEquals("JIRA_ISSUE_TYPE_RESOLUTION_AMBIGUOUS", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.TASK, null, null, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void prefersExactCriticalNameOverHighestSemanticFallback() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null, List.of(
                        new JiraCreateFieldAllowedValue("1", null, "Highest"),
                        new JiraCreateFieldAllowedValue("2", null, "Critical")
                ))
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.CRITICAL, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "2").equals(fields.get("priority"))));
    }

    @Test
    void prefersExactLowNameOverLowestSemanticFallback() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(priorityFields(
                new JiraCreateFieldAllowedValue("1", null, "Low"),
                new JiraCreateFieldAllowedValue("2", null, "Lowest")
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.LOW, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "1").equals(fields.get("priority"))));
    }

    @Test
    void prefersExactMediumNameOverBroadSemanticFallback() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(priorityFields(
                new JiraCreateFieldAllowedValue("1", null, "Medium"),
                new JiraCreateFieldAllowedValue("2", null, "Normal")
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.MEDIUM, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "1").equals(fields.get("priority"))));
    }

    @Test
    void deduplicatesSameProviderIdBeforeAutoPriorityResolution() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null, List.of(
                        new JiraCreateFieldAllowedValue("1", null, "High"),
                        new JiraCreateFieldAllowedValue("1", null, "High")
                ))
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.HIGH, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "1").equals(fields.get("priority"))));
    }

    @Test
    void usesSinglePrioritySemanticFallbackWhenNoExactNameExists() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null,
                        List.of(new JiraCreateFieldAllowedValue("1", null, "Highest")))
        ));
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.CRITICAL, null,
                        null, null, null, null, null));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("id", "1").equals(fields.get("priority"))));
    }

    @Test
    void failsClosedWhenMultiplePriorityFallbacksRemainWithoutExactName() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null, List.of(
                        new JiraCreateFieldAllowedValue("1", null, "Highest"),
                        new JiraCreateFieldAllowedValue("2", null, "Critical Fallback")
                ))
        ));

        assertEquals("JIRA_PRIORITY_RESOLUTION_AMBIGUOUS", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.CRITICAL, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void failsClosedWhenMultipleExactPriorityNamesHaveDistinctProviderIds() {
        CreateFixture fixture = fixture();
        when(fixture.provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null, List.of(
                        new JiraCreateFieldAllowedValue("1", null, "Critical"),
                        new JiraCreateFieldAllowedValue("2", null, "Critical")
                ))
        ));

        assertEquals("JIRA_PRIORITY_RESOLUTION_AMBIGUOUS", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", TaskType.TASK, null, Priority.CRITICAL, null,
                                null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void preservesRemoteSucceededWhenCanonicalFetchFails() {
        CreateFixture fixture = fixture();
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any()))
                .thenReturn(new JiraIssueReference("101", "P-1"));
        when(fixture.provider.getIssue("token", "cloud", "101")).thenThrow(
                IntegrationException.conflict("JIRA_RESOURCE_NOT_FOUND", "The selected Jira resource is no longer accessible"));

        assertEquals("JIRA_RESOURCE_NOT_FOUND", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null))).getCode());

        assertEquals(JiraWriteOperationStatus.REMOTE_SUCCEEDED, fixture.operation.getStatus());
        verify(fixture.operations).markRemoteSucceeded(fixture.operation.getId(), "101", "P-1");
        verify(fixture.operations, never()).failed(any(), any());
        verify(fixture.operations, never()).unknown(any(), any());
    }

    @Test
    void confirmsCanonicalTaskBeforeCompletingRemoteSuccess() {
        CreateFixture fixture = fixture();
        remoteCreateThenCanonicalFetch(fixture);

        fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null));

        InOrder ordered = inOrder(fixture.upserts, fixture.canonicalReads, fixture.operations);
        ordered.verify(fixture.upserts).upsert(eq(fixture.boardId), any(JiraIssueSnapshot.class));
        ordered.verify(fixture.canonicalReads).findResponse(fixture.projectId, "101");
        ordered.verify(fixture.operations).complete(fixture.operation.getId());
        assertEquals(JiraWriteOperationStatus.COMPLETED, fixture.operation.getStatus());
    }

    @Test
    void keepsRemoteSucceededWhenCanonicalTaskConfirmationFails() {
        CreateFixture fixture = fixture();
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any()))
                .thenReturn(new JiraIssueReference("101", "P-1"));
        when(fixture.provider.getIssue("token", "cloud", "101")).thenReturn(snapshot());
        when(fixture.canonicalReads.findResponse(fixture.projectId, "101")).thenReturn(Optional.empty());

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null))).getCode());

        assertEquals(JiraWriteOperationStatus.REMOTE_SUCCEEDED, fixture.operation.getStatus());
        verify(fixture.operations, never()).complete(fixture.operation.getId());
        verify(fixture.provider, times(1)).createIssue(eq("token"), eq("cloud"), any());
    }

    @Test
    void retriesSameKeyAfterConfirmationFailureWithoutAnotherRemoteCreate() {
        CreateFixture fixture = fixture();
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any()))
                .thenReturn(new JiraIssueReference("101", "P-1"));
        when(fixture.provider.getIssue("token", "cloud", "101")).thenReturn(snapshot());
        Task task = Task.builder().project(fixture.project).externalId("101").externalKey("P-1").title("Task").build();
        task.setId(UUID.randomUUID());
        when(fixture.canonicalReads.findResponse(fixture.projectId, "101"))
                .thenReturn(Optional.empty(), Optional.of(TaskReadResponse.from(task)));

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null))).getCode());
        assertEquals(JiraWriteOperationStatus.REMOTE_SUCCEEDED, fixture.operation.getStatus());

        assertEquals(task.getId(), fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null)).id());

        verify(fixture.provider, times(1)).createIssue(eq("token"), eq("cloud"), any());
        verify(fixture.operations).complete(fixture.operation.getId());
        assertEquals(JiraWriteOperationStatus.COMPLETED, fixture.operation.getStatus());
    }

    @Test
    void retriesRemoteSucceededWithCanonicalRecoveryWithoutAnotherCreate() {
        CreateFixture fixture = fixture();
        fixture.operation.setStatus(JiraWriteOperationStatus.REMOTE_SUCCEEDED);
        fixture.operation.setRemoteResourceId("101");
        fixture.operation.setRemoteResourceKey("P-1");
        when(fixture.provider.getIssue("token", "cloud", "101")).thenReturn(snapshot());
        Task task = Task.builder().project(fixture.project).externalId("101").externalKey("P-1").title("Task").build();
        task.setId(UUID.randomUUID());
        when(fixture.canonicalReads.findResponse(fixture.projectId, "101"))
                .thenReturn(Optional.of(TaskReadResponse.from(task)));

        assertEquals(task.getId(), fixture.service().create(fixture.principal, fixture.projectId, "key",
                new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null)).id());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
        verify(fixture.operations).complete(fixture.operation.getId());
        assertEquals(JiraWriteOperationStatus.COMPLETED, fixture.operation.getStatus());
    }

    @Test
    void failsSafeWhenCompletedReplayHasNoLocalTask() {
        CreateFixture fixture = fixture();
        fixture.operation.setStatus(JiraWriteOperationStatus.COMPLETED);
        fixture.operation.setRemoteResourceId("101");
        when(fixture.tasks.findByProjectIdAndExternalId(fixture.projectId, "101")).thenReturn(Optional.empty());

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", assertThrows(IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null))).getCode());

        verify(fixture.provider, never()).createIssue(any(), any(), any());
    }

    @Test
    void forwardsActiveButStaleAssigneeIdentityToProviderCreate() {
        CreateFixture fixture = fixture();
        UUID assigneeId = UUID.randomUUID();
        when(fixture.identities.findByStudentIdAndProvider(assigneeId, com.saga.be.entity.enums.IntegrationProvider.JIRA))
                .thenReturn(Optional.of(IdentityMap.builder()
                        .mappingStatus(IdentityMappingStatus.ACTIVE)
                        .externalAccountId("stale-account")
                        .build()));
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any())).thenThrow(
                IntegrationException.conflict("JIRA_RESOURCE_NOT_FOUND", "The selected Jira resource is no longer accessible"));

        String logged = captureCreateFailure(() -> assertEquals("JIRA_RESOURCE_NOT_FOUND", assertThrows(
                IntegrationException.class,
                () -> fixture.service().create(fixture.principal, fixture.projectId, "key",
                        new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, assigneeId))
        ).getCode()));

        verify(fixture.provider).createIssue(eq("token"), eq("cloud"), argThat(fields ->
                Map.of("accountId", "stale-account").equals(fields.get("assignee"))));
        verify(fixture.operations).failed(fixture.operation.getId(), "JIRA_RESOURCE_NOT_FOUND");
        assertTrue(logged.contains("stage=JIRA_PROVIDER_CREATE_ISSUE"));
        assertTrue(logged.contains("resourceType=PROJECT"));
    }

    private String captureCreateFailure(Runnable invocation) {
        Logger logger = (Logger) LoggerFactory.getLogger(JiraTaskWriteService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            invocation.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        } finally {
            logger.detachAppender(appender);
        }
    }

    private void remoteCreateThenCanonicalFetch(CreateFixture fixture) {
        when(fixture.provider.createIssue(eq("token"), eq("cloud"), any()))
                .thenReturn(new JiraIssueReference("101", "P-1"));
        JiraIssueSnapshot canonical = snapshot();
        when(fixture.provider.getIssue("token", "cloud", "101")).thenReturn(canonical);
        Task task = Task.builder().project(fixture.project).externalId("101").externalKey("P-1").title("Task").build();
        task.setId(UUID.randomUUID());
        when(fixture.canonicalReads.findResponse(fixture.projectId, "101"))
                .thenReturn(Optional.of(TaskReadResponse.from(task)));
    }

    private List<JiraCreateField> requiredFields() {
        return List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of())
        );
    }

    private List<JiraCreateField> priorityFields(JiraCreateFieldAllowedValue... values) {
        return List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("priority", "Priority", false, "string", null, List.of(values))
        );
    }

    private CreateFixture fixture() {
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraIssueUpsertService upserts = mock(JiraIssueUpsertService.class);
        JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        JiraCanonicalTaskReadService canonicalReads = mock(JiraCanonicalTaskReadService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        IdentityMapRepository identities = mock(IdentityMapRepository.class);
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").jiraProjectId("10000")
                .connectionStatus(IntegrationStatus.ACTIVE).grantedScopes("write:jira-work").build();
        UUID boardId = UUID.randomUUID();
        board.setId(boardId);
        JiraWriteOperation operation = JiraWriteOperation.builder().project(project)
                .status(JiraWriteOperationStatus.PENDING).build(); operation.setId(UUID.randomUUID());
        SagaPrincipal principal = new SagaPrincipal("sub", "a@b.test", "User", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        when(authorization.requireProjectManager(principal, projectId)).thenReturn(project);
        when(operations.fingerprint(any())).thenReturn("fingerprint");
        when(operations.claim(eq(project), eq(principal), eq(JiraWriteOperationType.TASK_CREATE), any(), eq("fingerprint")))
                .thenReturn(operation);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getCreateIssueTypes("token", "cloud", "10000"))
                .thenReturn(List.of(new JiraCreateIssueType("3", "Task", false, null)));
        when(provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of()),
                new JiraCreateField("assignee", "Assignee", false, "string", null, List.of())
        ));
        return new CreateFixture(authorization, boards, credentials, provider, upserts, operations, canonicalReads, tasks,
                identities, boardId, projectId, project, operation, principal);
    }

    private record CreateFixture(
            ProjectIntegrationAuthorizationService authorization,
            JiraBoardRepository boards,
            JiraCredentialService credentials,
            JiraProviderClient provider,
            JiraIssueUpsertService upserts,
            JiraWriteOperationService operations,
            JiraCanonicalTaskReadService canonicalReads,
            TaskRepository tasks,
            IdentityMapRepository identities,
            UUID boardId,
            UUID projectId,
            Project project,
            JiraWriteOperation operation,
            SagaPrincipal principal
    ) {
        private JiraTaskWriteService service() {
            return new JiraTaskWriteService(authorization, boards, credentials, provider, upserts, operations, canonicalReads, tasks,
                    identities, mock(SprintRepository.class), mock(JiraSprintUpsertService.class));
        }
    }

    private JiraIssueSnapshot snapshot() { return new JiraIssueSnapshot("101", "P-1", "Task", "Task", "To Do", null, null, null, null, null, null, LocalDateTime.now(), null, null, null, null, null); }
}
