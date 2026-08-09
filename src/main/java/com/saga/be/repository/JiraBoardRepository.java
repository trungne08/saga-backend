package com.saga.be.repository;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraBoardRepository extends JpaRepository<JiraBoard, UUID> {
    Optional<JiraBoard> findByProjectId(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select board from JiraBoard board where board.project.id = :projectId")
    Optional<JiraBoard> findForLinkByProjectId(@Param("projectId") UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select board from JiraBoard board where board.cloudId = :cloudId "
            + "and board.jiraProjectId = :jiraProjectId")
    Optional<JiraBoard> findForLinkByCloudIdAndJiraProjectId(
            @Param("cloudId") String cloudId,
            @Param("jiraProjectId") String jiraProjectId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select board from JiraBoard board where board.id = :id")
    Optional<JiraBoard> findForSyncClaimById(@Param("id") UUID id);

    /** Serializes refresh-token rotation for every caller of a Jira connection. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select board from JiraBoard board where board.id = :id")
    Optional<JiraBoard> findForCredentialRefreshById(@Param("id") UUID id);

    List<JiraBoard> findByConnectionStatusIn(List<IntegrationStatus> statuses);

    List<JiraBoard> findByProjectIdIn(List<UUID> projectIds);

    long countByConnectionStatus(IntegrationStatus connectionStatus);

    List<JiraBoard> findByWebhookExpiresAtBeforeAndConnectionStatusNot(
            LocalDateTime expiresBefore,
            IntegrationStatus excludedStatus
    );

    Optional<JiraBoard> findByWebhookSecretHashAndConnectionStatusNot(
            String webhookSecretHash,
            IntegrationStatus excludedStatus
    );
}
