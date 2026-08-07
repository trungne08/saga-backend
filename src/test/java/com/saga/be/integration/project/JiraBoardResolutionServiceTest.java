package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraAgileBoardInfo;
import com.saga.be.integration.provider.JiraBoardFeature;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.JiraBoardRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class JiraBoardResolutionServiceTest {
    @Test
    void validPersistedNumericIdDoesNotDiscoverAgain() {
        Fixture fixture = new Fixture();
        fixture.board.setJiraBoardId("42");

        assertEquals("42", fixture.service.resolve(fixture.board));

        verifyNoInteractions(fixture.provider, fixture.repository, fixture.credentials);
    }

    @Test
    void zeroOrKanbanOnlyBoardsFailWithNotFound() {
        for (List<JiraAgileBoardInfo> boards : List.of(
                List.<JiraAgileBoardInfo>of(),
                List.of(new JiraAgileBoardInfo("8", "Kanban", "kanban")))) {
            Fixture fixture = new Fixture();
            when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(boards);

            IntegrationException exception = assertThrows(IntegrationException.class,
                    () -> fixture.service.resolve(fixture.board));

            assertEquals("JIRA_SCRUM_BOARD_NOT_FOUND", exception.getCode());
            verify(fixture.repository, never()).saveAndFlush(any());
        }
    }

    @Test
    void multipleScrumBoardsFailClosed() {
        Fixture fixture = new Fixture();
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("8", "Sprint A", "scrum"),
                new JiraAgileBoardInfo("9", "Sprint B", "scrum")
        ));

        IntegrationException exception = assertThrows(IntegrationException.class,
                () -> fixture.service.resolve(fixture.board));

        assertEquals("JIRA_BOARD_SELECTION_REQUIRED", exception.getCode());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectedBoardDiscoveryLogsOnlySafeBoardFacts() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SAGA");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("8", "Kanban", "kanban", "10034", "SAGA")
        ));
        String message = rejectedDiscoveryMessage(fixture);
        assertTrue(message.contains("projectKey=SAGA"));
        assertTrue(message.contains("boardCount=1"));
        assertTrue(message.contains("boardTypes=[kanban]"));
        assertTrue(message.contains("boardIds=[8]"));
        assertTrue(message.contains("projectId=10034,projectKey=SAGA"));
        assertTrue(message.contains("discoveryRejectionReason=NO_SPRINT_CAPABLE_BOARD"));
    }

    @Test
    void zeroBoardRejectionEmitsCompleteSafeDiagnostics() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SAGA");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034"))
                .thenReturn(List.of());

        String message = rejectedDiscoveryMessage(fixture);

        assertTrue(message.contains("projectKey=SAGA"));
        assertTrue(message.contains("boardCount=0"));
        assertTrue(message.contains("boardTypes=[]"));
        assertTrue(message.contains("boardIds=[]"));
        assertTrue(message.contains("projectAssociations=[]"));
        assertTrue(message.contains("discoveryRejectionReason=NO_SPRINT_CAPABLE_BOARD"));
    }

    @Test
    void multipleScrumRejectionEmitsCompleteSafeDiagnostics() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SAGA");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("8", "Sprint A", "scrum", "10034", "SAGA"),
                new JiraAgileBoardInfo("9", "Sprint B", "scrum", "10034", "SAGA")
        ));

        String message = rejectedDiscoveryMessage(fixture);

        assertTrue(message.contains("projectKey=SAGA"));
        assertTrue(message.contains("boardCount=2"));
        assertTrue(message.contains("boardTypes=[scrum, scrum]"));
        assertTrue(message.contains("boardIds=[8, 9]"));
        assertTrue(message.contains("projectAssociations=[projectId=10034,projectKey=SAGA, "
                + "projectId=10034,projectKey=SAGA]"));
        assertTrue(message.contains("discoveryRejectionReason=MULTIPLE_SPRINT_CAPABLE_BOARDS"));
    }

    private String rejectedDiscoveryMessage(Fixture fixture) {
        Logger logger = (Logger) LoggerFactory.getLogger(JiraBoardResolutionService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertThrows(IntegrationException.class, () -> fixture.service.resolve(fixture.board));
        } finally {
            logger.detachAppender(events);
        }
        return events.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Jira board discovery rejected:"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void simpleBoardWithEnabledSprintsResolvesTheProductionSdpBoard() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SDP");
        JiraAgileBoardInfo sdpBoard = new JiraAgileBoardInfo(
                "35", "SDP board", "simple", "10034", "SDP"
        );
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034"))
                .thenReturn(List.of(sdpBoard));
        when(fixture.provider.getBoardFeatures("token", "cloud", "35")).thenReturn(List.of(
                new JiraBoardFeature("SPRINTS", "sprints", "ENABLED", "35")
        ));
        when(fixture.repository.findById(fixture.board.getId())).thenReturn(Optional.of(fixture.board));

        assertEquals("35", fixture.service.resolve(fixture.board));

        assertEquals("35", fixture.board.getJiraBoardId());
        verify(fixture.repository).saveAndFlush(fixture.board);
    }

    @Test
    void simpleBoardWithDisabledSprintsFailsWithDedicatedCategory() {
        Fixture fixture = simpleBoardFixture("DISABLED");

        IntegrationException exception = assertThrows(
                IntegrationException.class, () -> fixture.service.resolve(fixture.board)
        );

        assertEquals("JIRA_SPRINTS_NOT_ENABLED", exception.getCode());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void simpleBoardWithoutConfirmedSprintsFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SDP");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("35", "SDP board", "simple", "10034", "SDP")
        ));
        when(fixture.provider.getBoardFeatures("token", "cloud", "35")).thenReturn(List.of(
                new JiraBoardFeature("BACKLOG", "backlog", "ENABLED", "35")
        ));

        IntegrationException exception = assertThrows(
                IntegrationException.class, () -> fixture.service.resolve(fixture.board)
        );

        assertEquals("JIRA_SCRUM_BOARD_NOT_FOUND", exception.getCode());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void multipleSprintCapableBoardsRequireSelection() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SDP");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("8", "Scrum", "scrum", "10034", "SDP"),
                new JiraAgileBoardInfo("35", "Simple", "simple", "10034", "SDP")
        ));
        when(fixture.provider.getBoardFeatures("token", "cloud", "35")).thenReturn(List.of(
                new JiraBoardFeature("SPRINTS", "sprints", "ENABLED", "35")
        ));

        assertEquals("JIRA_BOARD_SELECTION_REQUIRED", assertThrows(
                IntegrationException.class, () -> fixture.service.resolve(fixture.board)
        ).getCode());
        verify(fixture.repository, never()).saveAndFlush(any());
    }

    @Test
    void mismatchedProjectAssociationIsRejectedWithoutFeatureLookup() {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SDP");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("35", "Other board", "simple", "99999", "OTHER")
        ));

        assertEquals("JIRA_SCRUM_BOARD_NOT_FOUND", assertThrows(
                IntegrationException.class, () -> fixture.service.resolve(fixture.board)
        ).getCode());
        verify(fixture.provider, never()).getBoardFeatures(any(), any(), any());
    }

    @Test
    void simpleBoardCapabilityDiagnosticContainsOnlySafeMachineFacts() {
        Fixture fixture = simpleBoardFixture("ENABLED");
        fixture.board.setProjectKey("SDP");
        Logger logger = (Logger) LoggerFactory.getLogger(JiraBoardResolutionService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertThrows(IntegrationException.class, () -> fixture.service.resolve(fixture.board));
        } finally {
            logger.detachAppender(events);
        }

        String message = events.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(value -> value.startsWith("Jira board capability evaluated:"))
                .findFirst()
                .orElseThrow();
        assertTrue(message.contains("projectKey=SDP"));
        assertTrue(message.contains("boardId=35"));
        assertTrue(message.contains("boardType=simple"));
        assertTrue(message.contains("boardFeatureIdentifiers=[SPRINTS]"));
        assertTrue(message.contains("sprintFeatureState=ENABLED"));
        assertTrue(message.contains("candidateReason=SPRINT_FEATURE_ENABLED"));
        assertTrue(message.contains("selectionResult=CANDIDATE"));
    }

    private Fixture simpleBoardFixture(String sprintState) {
        Fixture fixture = new Fixture();
        fixture.board.setProjectKey("SDP");
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034")).thenReturn(List.of(
                new JiraAgileBoardInfo("35", "SDP board", "simple", "10034", "SDP")
        ));
        when(fixture.provider.getBoardFeatures("token", "cloud", "35")).thenReturn(List.of(
                new JiraBoardFeature("SPRINTS", "sprints", sprintState, "35")
        ));
        return fixture;
    }

    @Test
    void oneScrumBoardPersistsOnlyExternalNumericId() {
        Fixture fixture = new Fixture();
        when(fixture.provider.discoverAgileBoards("token", "cloud", "10034"))
                .thenReturn(List.of(new JiraAgileBoardInfo("123", "Sprint", "scrum")));
        when(fixture.repository.findById(fixture.board.getId())).thenReturn(Optional.of(fixture.board));

        assertEquals("123", fixture.service.resolve(fixture.board));

        assertEquals("123", fixture.board.getJiraBoardId());
        verify(fixture.repository).saveAndFlush(fixture.board);
    }

    private static final class Fixture {
        final JiraBoardRepository repository = mock(JiraBoardRepository.class);
        final JiraProviderClient provider = mock(JiraProviderClient.class);
        final JiraCredentialService credentials = mock(JiraCredentialService.class);
        final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        final JiraBoard board = JiraBoard.builder().cloudId("cloud").jiraProjectId("10034").build();
        final JiraBoardResolutionService service;

        Fixture() {
            board.setId(UUID.randomUUID());
            when(credentials.validAccessToken(board)).thenReturn("token");
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            service = new JiraBoardResolutionService(repository, provider, credentials, transactions);
        }
    }
}
