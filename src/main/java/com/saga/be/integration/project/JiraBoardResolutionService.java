package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraAgileBoardInfo;
import com.saga.be.integration.provider.JiraBoardFeature;
import com.saga.be.integration.provider.JiraProjectFeature;
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
            if (candidates.stream().anyMatch(BoardCandidate::capabilityUnconfirmed)) {
                logDiscoveryRejection(board, discoveredBoards, "SPRINT_CAPABILITY_UNCONFIRMED");
                throw IntegrationException.conflict(
                        "JIRA_SPRINT_CAPABILITY_UNCONFIRMED",
                        "Sprint capability could not be confirmed for the linked Jira project"
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
        BoardCandidate selected = sprintCapableBoards.get(0);
        logSelectedCandidate(board, selected);
        String discovered = selected.board().boardId();
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
            return logCandidate(board, discoveredBoard, List.of(), "NOT_REQUESTED", "NOT_REQUESTED",
                    "NOT_REQUESTED",
                    "PROJECT_ASSOCIATION_MISMATCH", false, false);
        }
        if ("scrum".equalsIgnoreCase(discoveredBoard.type())) {
            return logCandidate(board, discoveredBoard, List.of(), "NOT_APPLICABLE", "NOT_REQUESTED",
                    "NOT_REQUESTED",
                    "SCRUM_BOARD_TYPE", true, false);
        }
        if (!"simple".equalsIgnoreCase(discoveredBoard.type())) {
            return logCandidate(board, discoveredBoard, List.of(), "NOT_REQUESTED", "NOT_REQUESTED",
                    "NOT_REQUESTED",
                    "NON_SPRINT_BOARD_TYPE", false, false);
        }

        List<JiraBoardFeature> boardFeatures = provider.getBoardFeatures(
                accessToken, board.getCloudId(), discoveredBoard.boardId());
        List<JiraProjectFeature> projectFeatures = provider.getProjectFeatures(
                accessToken, board.getCloudId(), board.getJiraProjectId());
        List<String> boardFeatureIdentifiers = boardFeatures.stream()
                .map(JiraBoardFeature::identifier)
                .filter(java.util.Objects::nonNull)
                .toList();
        logProjectFeatureDiagnostics(board, projectFeatures);
        try {
            if (provider.supportsBoardSprintEndpoint(
                    accessToken, board.getCloudId(), discoveredBoard.boardId())) {
                return logCandidate(board, discoveredBoard, boardFeatureIdentifiers, "CONFIRMED", "200",
                        "SUPPORTED", "SPRINT_ENDPOINT_SUPPORTED", true, false);
            }
            return logCandidate(board, discoveredBoard, boardFeatureIdentifiers, "UNCONFIRMED", "UNKNOWN",
                    "UNCONFIRMED", "SIMPLE_CAPABILITY_UNCONFIRMED", false, true);
        } catch (IntegrationException exception) {
            logCandidate(board, discoveredBoard, boardFeatureIdentifiers,
                    probeState(exception), probeHttpStatus(exception), probeResult(exception),
                    probeCandidateReason(exception), false,
                    "JIRA_SPRINT_CAPABILITY_UNCONFIRMED".equals(exception.getCode()));
            throw exception;
        }
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
            String sprintCapabilityProbeHttpStatus,
            String sprintCapabilityProbeResult,
            String candidateReason,
            boolean sprintCapable,
            boolean capabilityUnconfirmed
    ) {
        String selectionResult = sprintCapable ? "CANDIDATE" : "REJECTED";
        log.info("Jira board capability evaluated: projectKey={}, boardId={}, boardType={}, "
                        + "boardFeatureIdentifiers={}, sprintFeatureState={}, sprintCapabilityProbeHttpStatus={}, "
                        + "sprintCapabilityProbeResult={}, candidateReason={}, selectionResult={}",
                linkedBoard.getProjectKey(), discoveredBoard.boardId(), discoveredBoard.type(), featureIdentifiers,
                sprintFeatureState, sprintCapabilityProbeHttpStatus, sprintCapabilityProbeResult,
                candidateReason, selectionResult);
        return new BoardCandidate(discoveredBoard, sprintCapable, capabilityUnconfirmed);
    }

    private void logSelectedCandidate(JiraBoard linkedBoard, BoardCandidate selected) {
        String sprintCapabilityProbeHttpStatus = "scrum".equalsIgnoreCase(selected.board().type())
                ? "NOT_REQUESTED" : "200";
        String sprintCapabilityProbeResult = "scrum".equalsIgnoreCase(selected.board().type())
                ? "NOT_REQUESTED" : "SUPPORTED";
        String candidateReason = "scrum".equalsIgnoreCase(selected.board().type())
                ? "SCRUM_BOARD_TYPE" : "SPRINT_ENDPOINT_SUPPORTED";
        log.info("Jira board capability selected: projectKey={}, boardId={}, boardType={}, "
                        + "sprintCapabilityProbeHttpStatus={}, sprintCapabilityProbeResult={}, "
                        + "candidateReason={}, selectionResult={}",
                linkedBoard.getProjectKey(), selected.board().boardId(), selected.board().type(),
                sprintCapabilityProbeHttpStatus, sprintCapabilityProbeResult,
                candidateReason, "SELECTED");
    }

    private String probeState(IntegrationException exception) {
        return "JIRA_SPRINT_CAPABILITY_UNCONFIRMED".equals(exception.getCode())
                ? "UNCONFIRMED" : "PROBE_FAILED";
    }

    private String probeHttpStatus(IntegrationException exception) {
        return switch (exception.getCode()) {
            case "JIRA_SPRINT_CAPABILITY_UNCONFIRMED" -> "400";
            case "JIRA_ACCESS_REVOKED" -> "401";
            case "JIRA_ACCESS_FORBIDDEN" -> "403";
            case "JIRA_BOARD_NOT_FOUND" -> "404";
            case "JIRA_RATE_LIMITED" -> "429";
            case "JIRA_RESPONSE_INVALID" -> "200";
            case "JIRA_PROVIDER_UNAVAILABLE" -> "UNAVAILABLE";
            default -> "UNKNOWN";
        };
    }

    private String probeResult(IntegrationException exception) {
        return "JIRA_SPRINT_CAPABILITY_UNCONFIRMED".equals(exception.getCode())
                ? "UNCONFIRMED" : "FAILED";
    }

    private String probeCandidateReason(IntegrationException exception) {
        return switch (exception.getCode()) {
            case "JIRA_SPRINT_CAPABILITY_UNCONFIRMED" -> "SPRINT_ENDPOINT_UNCONFIRMED";
            case "JIRA_ACCESS_REVOKED" -> "SPRINT_ENDPOINT_ACCESS_REVOKED";
            case "JIRA_ACCESS_FORBIDDEN" -> "SPRINT_ENDPOINT_ACCESS_FORBIDDEN";
            case "JIRA_BOARD_NOT_FOUND" -> "SPRINT_ENDPOINT_BOARD_NOT_FOUND";
            case "JIRA_RATE_LIMITED" -> "SPRINT_ENDPOINT_RATE_LIMITED";
            case "JIRA_RESPONSE_INVALID" -> "SPRINT_ENDPOINT_RESPONSE_INVALID";
            case "JIRA_PROVIDER_UNAVAILABLE" -> "SPRINT_ENDPOINT_UNAVAILABLE";
            default -> "SPRINT_ENDPOINT_PROBE_FAILED";
        };
    }

    private void logProjectFeatureDiagnostics(
            JiraBoard board,
            List<JiraProjectFeature> projectFeatures
    ) {
        log.info("Jira project feature discovery: projectKey={}, projectFeatureIdentifiers={}, "
                        + "projectFeatureStates={}",
                board.getProjectKey(),
                projectFeatures.stream()
                        .map(JiraProjectFeature::feature)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                projectFeatures.stream()
                        .map(JiraProjectFeature::state)
                        .filter(java.util.Objects::nonNull)
                        .toList());
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
            boolean capabilityUnconfirmed
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
