package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.service.TeamContributionRefreshService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import(JiraIssueUpsertService.class)
class JiraRequestPersistenceIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JiraBoardRepository boardRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JiraIssueUpsertService upsertService;

    @MockitoBean
    private IdentityMappingService identityMappingService;

    @MockitoBean
    private TeamContributionRefreshService teamContributionRefreshService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void canonicalRequestSaveAndFlushesAndRereadsAsRequest() {
        Project project = projectRepository.saveAndFlush(
                Project.builder().name("Request persistence project").build()
        );
        JiraBoard board = boardRepository.saveAndFlush(JiraBoard.builder()
                .project(project)
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build());
        LocalDateTime updatedAt = LocalDateTime.parse("2026-08-13T05:00:00");
        JiraIssueSnapshot request = new JiraIssueSnapshot(
                "request-1", "SAGA-REQUEST-1", "Canonical request",
                "Request", "To Do", "Medium", null, null, null, null,
                updatedAt.minusDays(1), updatedAt,
                null, null, null, null
        );

        assertTrue(upsertService.upsert(board.getId(), request));

        assertEquals(TaskType.REQUEST, taskRepository
                .findByProjectIdAndExternalId(project.getId(), "request-1")
                .orElseThrow()
                .getType());
    }
}
