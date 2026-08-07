package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraAgileBoardInfo;
import com.saga.be.integration.provider.JiraBoardFeature;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.JiraBoardRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Resolves the external Jira Software board id without ever using the local UUID. */
@Service
public class JiraBoardResolutionService {
    private static final String NUMERIC_BOARD_ID = "\\d+";
    private static final Logger log = LoggerFactory.getLogger(JiraBoardResolutionService.class);

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
        List<JiraAgileBoardInfo> discoveredBoards = provider.discoverAgileBoards(
                accessToken, board.getCloudId(), board.getJiraProjectId());
        List<BoardCandidate> candidates = discoveredBoards.stream()
                .map(value -> assessCandidate(board, accessToken, value))
                .toList();
        List<BoardCandidate> sprintCapableBoards = candidates.stream()
                .filter(BoardCandidate::sprintCapable)
                .toList();
        if (sprintCapableBoards.isEmpty()) {
            if (candidates.stream().anyMatch(BoardCandidate::sprintsDisabled)) {
                logDiscoveryRejection(board, discoveredBoards, "SPRINT_FEATURE_DISABLED");
                throw IntegrationException.conflict(
                        "JIRA_SPRINTS_NOT_ENABLED",
                        "Sprints are not enabled for the linked Jira project"
                );
            }
            logDiscoveryRejection(board, discoveredBoards, "NO_SPRINT_CAPABLE_BOARD");
            throw IntegrationException.conflict("JIRA_SCRUM_BOARD_NOT_FOUND",
                    "No Scrum board is available for the linked Jira project");
        }
        if (sprintCapableBoards.size() != 1) {
            logDiscoveryRejection(board, discoveredBoards, "MULTIPLE_SPRINT_CAPABLE_BOARDS");
            throw IntegrationException.conflict("JIRA_BOARD_SELECTION_REQUIRED",
                    "More than one Sprint-capable board is available for the linked Jira project");
        }
        String discovered = sprintCapableBoards.get(0).board().boardId();
        if (!isExternalBoardId(discovered)) {
            throw IntegrationException.unavailable("JIRA_RESPONSE_INVALID");
        }
        return discovered;
    }

    private BoardCandidate assessCandidate(
            JiraBoard board,
            String accessToken,
            JiraAgileBoardInfo discoveredBoard
    ) {
        if (!matchesLinkedProject(board, discoveredBoard)) {
            return logCandidate(board, discoveredBoard, List.of(), "NOT_REQUESTED",
                    "PROJECT_ASSOCIATION_MISMATCH", false, false);
        }
        if ("scrum".equalsIgnoreCase(discoveredBoard.type())) {
            return logCandidate(board, discoveredBoard, List.of(), "NOT_APPLICABLE",
                    "SCRUM_BOARD_TYPE", true, false);
        }
        if (!"simple".equalsIgnoreCase(discoveredBoard.type())) {
            return logCandidate(board, discoveredBoard, List.of(), "NOT_REQUESTED",
                    "NON_SPRINT_BOARD_TYPE", false, false);
        }

        List<JiraBoardFeature> features = provider.getBoardFeatures(
                accessToken, board.getCloudId(), discoveredBoard.boardId());
        List<String> identifiers = features.stream().map(JiraBoardFeature::identifier).toList();
        List<JiraBoardFeature> sprintFeatures = features.stream()
                .filter(JiraBoardFeature::isSprintsFeature)
                .toList();
        if (sprintFeatures.stream().anyMatch(feature ->
                JiraBoardFeature.ENABLED_STATE.equals(feature.state()))) {
            return logCandidate(board, discoveredBoard, identifiers, JiraBoardFeature.ENABLED_STATE,
                    "SPRINT_FEATURE_ENABLED", true, false);
        }
        if (sprintFeatures.stream().anyMatch(feature ->
                JiraBoardFeature.DISABLED_STATE.equals(feature.state()))) {
            return logCandidate(board, discoveredBoard, identifiers, JiraBoardFeature.DISABLED_STATE,
                    "SPRINT_FEATURE_DISABLED", false, true);
        }
        String state = sprintFeatures.isEmpty() ? "NOT_REPORTED" : "UNCONFIRMED";
        return logCandidate(board, discoveredBoard, identifiers, state,
                "SPRINT_FEATURE_UNCONFIRMED", false, false);
    }

    private boolean matchesLinkedProject(JiraBoard board, JiraAgileBoardInfo discoveredBoard) {
        boolean projectIdMatches = discoveredBoard.locationProjectId() == null
                || discoveredBoard.locationProjectId().equals(board.getJiraProjectId());
        boolean projectKeyMatches = discoveredBoard.locationProjectKey() == null
                || (board.getProjectKey() != null
                && discoveredBoard.locationProjectKey().equalsIgnoreCase(board.getProjectKey()));
        return projectIdMatches && projectKeyMatches;
    }

    private BoardCandidate logCandidate(
            JiraBoard linkedBoard,
            JiraAgileBoardInfo discoveredBoard,
            List<String> featureIdentifiers,
            String sprintFeatureState,
            String candidateReason,
            boolean sprintCapable,
            boolean sprintsDisabled
    ) {
        String selectionResult = sprintCapable ? "CANDIDATE" : "REJECTED";
        log.info("Jira board capability evaluated: projectKey={}, boardId={}, boardType={}, "
                        + "boardFeatureIdentifiers={}, sprintFeatureState={}, candidateReason={}, "
                        + "selectionResult={}",
                linkedBoard.getProjectKey(), discoveredBoard.boardId(), discoveredBoard.type(), featureIdentifiers,
                sprintFeatureState, candidateReason, selectionResult);
        return new BoardCandidate(discoveredBoard, sprintCapable, sprintsDisabled);
    }

    private void logDiscoveryRejection(
            JiraBoard board,
            List<JiraAgileBoardInfo> discoveredBoards,
            String rejectionReason
    ) {
        log.warn("Jira board discovery rejected: projectKey={}, boardCount={}, boardTypes={}, "
                        + "boardIds={}, projectAssociations={}, discoveryRejectionReason={}",
                board.getProjectKey(),
                discoveredBoards.size(),
                discoveredBoards.stream().map(JiraAgileBoardInfo::type).toList(),
                discoveredBoards.stream().map(JiraAgileBoardInfo::boardId).toList(),
                discoveredBoards.stream().map(JiraAgileBoardInfo::projectAssociation).toList(),
                rejectionReason);
    }

    private record BoardCandidate(
            JiraAgileBoardInfo board,
            boolean sprintCapable,
            boolean sprintsDisabled
    ) {
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
