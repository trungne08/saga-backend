package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraAgileBoardInfo;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.JiraBoardRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Resolves the external Jira Software board id without ever using the local UUID. */
@Service
public class JiraBoardResolutionService {
    private static final String NUMERIC_BOARD_ID = "\\d+";

    private final JiraBoardRepository boards;
    private final JiraProviderClient provider;
    private final JiraCredentialService credentials;
    private final TransactionTemplate transactions;

    public JiraBoardResolutionService(
            JiraBoardRepository boards,
            JiraProviderClient provider,
            JiraCredentialService credentials,
            PlatformTransactionManager transactionManager
    ) {
        this.boards = boards;
        this.provider = provider;
        this.credentials = credentials;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String resolve(JiraBoard board) {
        if (isExternalBoardId(board.getJiraBoardId())) {
            return board.getJiraBoardId();
        }
        return resolve(board, credentials.validAccessToken(board));
    }

    /** Provider I/O happens before the short local persistence transaction. */
    public String resolve(JiraBoard board, String accessToken) {
        if (isExternalBoardId(board.getJiraBoardId())) {
            return board.getJiraBoardId();
        }
        if (board.getId() == null || board.getCloudId() == null || board.getJiraProjectId() == null) {
            throw IntegrationException.conflict(
                    "JIRA_BOARD_NOT_CONFIGURED",
                    "The Jira project link is not fully configured"
            );
        }
        String discovered = discover(board, accessToken);
        String resolved = transactions.execute(status -> persistResolved(board.getId(), discovered));
        board.setJiraBoardId(resolved);
        return resolved;
    }

    /**
     * Used while an OAuth link is being assembled. The caller owns the managed
     * entity, so it must persist it in its existing local transaction.
     */
    public String resolveForLinking(JiraBoard board, String accessToken) {
        if (isExternalBoardId(board.getJiraBoardId())) {
            return board.getJiraBoardId();
        }
        String discovered = discover(board, accessToken);
        board.setJiraBoardId(discovered);
        return discovered;
    }

    private String discover(JiraBoard board, String accessToken) {
        if (board.getCloudId() == null || board.getJiraProjectId() == null) {
            throw IntegrationException.conflict(
                    "JIRA_BOARD_NOT_CONFIGURED",
                    "The Jira project link is not fully configured"
            );
        }
        List<JiraAgileBoardInfo> scrumBoards = provider.discoverAgileBoards(
                        accessToken, board.getCloudId(), board.getJiraProjectId())
                .stream().filter(value -> "scrum".equalsIgnoreCase(value.type())).toList();
        if (scrumBoards.isEmpty()) {
            throw IntegrationException.conflict("JIRA_SCRUM_BOARD_NOT_FOUND",
                    "No Scrum board is available for the linked Jira project");
        }
        if (scrumBoards.size() != 1) {
            throw IntegrationException.conflict("JIRA_BOARD_SELECTION_REQUIRED",
                    "More than one Scrum board is available for the linked Jira project");
        }
        String discovered = scrumBoards.get(0).boardId();
        if (!isExternalBoardId(discovered)) {
            throw IntegrationException.unavailable("JIRA_RESPONSE_INVALID");
        }
        return discovered;
    }

    private String persistResolved(java.util.UUID boardEntityId, String discovered) {
        JiraBoard current = boards.findById(boardEntityId).orElseThrow(() ->
                IntegrationException.conflict("JIRA_LINK_NOT_FOUND", "The Jira project link does not exist")
        );
        if (isExternalBoardId(current.getJiraBoardId())) {
            if (!current.getJiraBoardId().equals(discovered)) {
                throw IntegrationException.conflict(
                        "JIRA_BOARD_SELECTION_REQUIRED",
                        "Concurrent board resolution produced a different board"
                );
            }
            return current.getJiraBoardId();
        }
        current.setJiraBoardId(discovered);
        boards.saveAndFlush(current);
        return discovered;
    }

    public static boolean isExternalBoardId(String value) {
        return value != null && value.matches(NUMERIC_BOARD_ID);
    }
}
