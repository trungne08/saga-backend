package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraBoardRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class JiraBoardLinkPersistenceServiceTest {

    @Mock
    private JiraBoardRepository boards;

    @Mock
    private JiraCredentialService credentials;

    private JiraBoardLinkPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new JiraBoardLinkPersistenceService(boards, credentials);
        lenient().when(credentials.encryptAccess(any(), any())).thenReturn("fresh-access-ciphertext");
        lenient().when(credentials.encryptRefresh(any(), any())).thenReturn("fresh-refresh-ciphertext");
        lenient().when(boards.saveAndFlush(any(JiraBoard.class))).thenAnswer(invocation -> {
            JiraBoard board = invocation.getArgument(0);
            if (board.getId() == null) {
                board.setId(UUID.randomUUID());
            }
            return board;
        });
    }

    @Test
    void firstLinkCreatesBoardAfterLockingBothIdentities() {
        JiraBoardLinkCommand command = command(project());
        when(boards.findForLinkByProjectId(command.project().getId())).thenReturn(Optional.empty());
        when(boards.findForLinkByCloudIdAndJiraProjectId("cloud-a", "10034"))
                .thenReturn(Optional.empty());

        JiraBoard saved = service.upsert(command);

        assertEquals(command.project().getId(), saved.getProject().getId());
        assertEquals("10034", saved.getJiraProjectId());
        assertEquals("99", saved.getJiraBoardId());
        assertEquals("fresh-access-ciphertext", saved.getEncryptedAccessToken());
        assertEquals(IntegrationStatus.CONNECTING, saved.getConnectionStatus());
        verify(boards).findForLinkByProjectId(command.project().getId());
        verify(boards).findForLinkByCloudIdAndJiraProjectId("cloud-a", "10034");
    }

    @Test
    void relinkMatchingRetainedProjectRowUpdatesSameBoardId() {
        Project project = project();
        JiraBoard retained = board(project, "cloud-a", "10034");
        retained.setConnectionStatus(IntegrationStatus.DISCONNECTED);
        JiraBoardLinkCommand command = command(project);
        when(boards.findForLinkByProjectId(project.getId())).thenReturn(Optional.of(retained));
        when(boards.findForLinkByCloudIdAndJiraProjectId("cloud-a", "10034"))
                .thenReturn(Optional.of(retained));

        JiraBoard saved = service.upsert(command);

        assertEquals(retained.getId(), saved.getId());
        assertEquals("fresh-access-ciphertext", saved.getEncryptedAccessToken());
        assertEquals(IntegrationStatus.CONNECTING, saved.getConnectionStatus());
    }

    @Test
    void providerIdentityRowForSameProjectIsReusedWhenProjectLookupMisses() {
        Project project = project();
        JiraBoard retained = board(project, "cloud-a", "10034");
        JiraBoardLinkCommand command = command(project);
        when(boards.findForLinkByProjectId(project.getId())).thenReturn(Optional.empty());
        when(boards.findForLinkByCloudIdAndJiraProjectId("cloud-a", "10034"))
                .thenReturn(Optional.of(retained));

        JiraBoard saved = service.upsert(command);

        assertEquals(retained.getId(), saved.getId());
        verify(boards, never()).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                board -> board != retained && board.getId() == null
        ));
    }

    @Test
    void providerIdentityOwnedByAnotherProjectFailsClosedWithoutInsert() {
        Project requestedProject = project();
        JiraBoard linkedElsewhere = board(project(), "cloud-a", "10034");
        JiraBoardLinkCommand command = command(requestedProject);
        when(boards.findForLinkByProjectId(requestedProject.getId())).thenReturn(Optional.empty());
        when(boards.findForLinkByCloudIdAndJiraProjectId("cloud-a", "10034"))
                .thenReturn(Optional.of(linkedElsewhere));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.upsert(command)
        );

        assertEquals("JIRA_PROJECT_ALREADY_LINKED", exception.getCode());
        assertEquals("This Jira project is already linked to another SAGA project", exception.getMessage());
        verify(boards, never()).saveAndFlush(any());
    }

    @Test
    void retainedProjectCannotSilentlyChangeProviderIdentity() {
        Project project = project();
        JiraBoard retained = board(project, "cloud-a", "10034");
        JiraBoardLinkCommand differentProvider = command(project, "cloud-b", "20000");
        when(boards.findForLinkByProjectId(project.getId())).thenReturn(Optional.of(retained));
        when(boards.findForLinkByCloudIdAndJiraProjectId("cloud-b", "20000"))
                .thenReturn(Optional.empty());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.upsert(differentProvider)
        );

        assertEquals("JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED", exception.getCode());
        verify(boards, never()).saveAndFlush(any());
        assertEquals("cloud-a", retained.getCloudId());
        assertEquals("10034", retained.getJiraProjectId());
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        return project;
    }

    private JiraBoard board(Project project, String cloudId, String jiraProjectId) {
        JiraBoard board = JiraBoard.builder()
                .project(project)
                .cloudId(cloudId)
                .jiraProjectId(jiraProjectId)
                .connectionStatus(IntegrationStatus.DISCONNECTED)
                .build();
        board.setId(UUID.randomUUID());
        return board;
    }

    private JiraBoardLinkCommand command(Project project) {
        return command(project, "cloud-a", "10034");
    }

    private JiraBoardLinkCommand command(Project project, String cloudId, String jiraProjectId) {
        return new JiraBoardLinkCommand(
                project,
                "SAGA Project",
                cloudId,
                "https://site.example",
                jiraProjectId,
                "SAGA",
                "99",
                "fresh-access-token",
                "fresh-refresh-token",
                Instant.parse("2026-08-07T08:00:00Z"),
                Set.of("read:jira-work"),
                "actor-sub",
                null
        );
    }
}
