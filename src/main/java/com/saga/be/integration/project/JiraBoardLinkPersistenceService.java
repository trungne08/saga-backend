package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.BoardType;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraBoardRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a verified Jira link without holding a database lock during provider I/O.
 * A Jira board is both a Project-owned history anchor and a provider-identity record.
 */
@Service
public class JiraBoardLinkPersistenceService {

    private final JiraBoardRepository boards;
    private final JiraCredentialService credentials;

    public JiraBoardLinkPersistenceService(
            JiraBoardRepository boards,
            JiraCredentialService credentials
    ) {
        this.boards = boards;
        this.credentials = credentials;
    }

    @Transactional
    public JiraBoard upsert(JiraBoardLinkCommand command) {
        Optional<JiraBoard> byProject = boards.findForLinkByProjectId(command.project().getId());
        Optional<JiraBoard> byProvider = boards.findForLinkByCloudIdAndJiraProjectId(
                command.cloudId(),
                command.jiraProjectId()
        );
        JiraBoard board = resolve(command, byProject, byProvider);
        boolean newBoard = board.getId() == null;

        applyVerifiedIdentity(board, command);
        if (newBoard) {
            board = boards.saveAndFlush(board);
        }
        board.setEncryptedAccessToken(credentials.encryptAccess(board, command.accessToken()));
        board.setEncryptedRefreshToken(credentials.encryptRefresh(board, command.refreshToken()));
        board.setTokenExpiresAt(LocalDateTime.ofInstant(command.tokenExpiresAt(), ZoneOffset.UTC));
        board.setGrantedScopes(String.join(" ", command.grantedScopes()));
        return boards.saveAndFlush(board);
    }

    @Transactional
    public JiraBoard complete(JiraBoardLinkCommand command, String webhookId,
            boolean webhookCreated, String webhookSecretHash) {
        JiraBoard board = boards.findForLinkByProjectId(command.project().getId())
                .orElseThrow(() -> IntegrationException.conflict(
                        "JIRA_LINK_NOT_FOUND",
                        "The Jira project link does not exist"
                ));
        if (!sameProviderIdentity(board, command)) {
            throw IntegrationException.conflict(
                    "JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED",
                    "This SAGA project cannot be relinked to a different Jira project"
            );
        }
        board.setWebhookId(webhookId);
        if (webhookCreated) {
            board.setWebhookSecretHash(webhookSecretHash);
        }
        board.setWebhookExpiresAt(LocalDateTime.now().plusDays(29));
        board.setConnectionStatus(IntegrationStatus.BACKFILLING);
        board.setConsecutiveFailures(0);
        return boards.saveAndFlush(board);
    }

    private JiraBoard resolve(
            JiraBoardLinkCommand command,
            Optional<JiraBoard> byProject,
            Optional<JiraBoard> byProvider
    ) {
        JiraBoard projectBoard = byProject.orElse(null);
        JiraBoard providerBoard = byProvider.orElse(null);
        if (providerBoard != null && !Objects.equals(
                providerBoard.getProject().getId(), command.project().getId()
        )) {
            throw IntegrationException.conflict(
                    "JIRA_PROJECT_ALREADY_LINKED",
                    "This Jira project is already linked to another SAGA project"
            );
        }
        if (projectBoard != null && providerBoard != null
                && !Objects.equals(projectBoard.getId(), providerBoard.getId())) {
            throw IntegrationException.conflict(
                    "JIRA_PROJECT_ALREADY_LINKED",
                    "This Jira project is already linked to another SAGA project"
            );
        }
        if (providerBoard != null) {
            return providerBoard;
        }
        if (projectBoard != null) {
            if (projectBoard.getJiraProjectId() != null
                    && !sameProviderIdentity(projectBoard, command)) {
                throw IntegrationException.conflict(
                        "JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED",
                        "This SAGA project cannot be relinked to a different Jira project"
                );
            }
            return projectBoard;
        }
        return JiraBoard.builder()
                .project(command.project())
                .type(BoardType.OTHER)
                .connectionStatus(IntegrationStatus.CONNECTING)
                .build();
    }

    private void applyVerifiedIdentity(JiraBoard board, JiraBoardLinkCommand command) {
        board.setName(command.name());
        board.setCloudId(command.cloudId());
        board.setSiteUrl(command.siteUrl());
        board.setJiraProjectId(command.jiraProjectId());
        board.setProjectKey(command.projectKey());
        board.setJiraBoardId(command.jiraBoardId());
        board.setConnectedByCognitoSub(command.connectedByCognitoSub());
        board.setConnectedByStudent(command.connectedByStudent());
        board.setConnectionStatus(IntegrationStatus.CONNECTING);
    }

    private boolean sameProviderIdentity(JiraBoard board, JiraBoardLinkCommand command) {
        return Objects.equals(board.getCloudId(), command.cloudId())
                && Objects.equals(board.getJiraProjectId(), command.jiraProjectId());
    }
}
