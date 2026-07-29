package com.saga.be.repository;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraBoardRepository extends JpaRepository<JiraBoard, UUID> {
    Optional<JiraBoard> findByProjectId(UUID projectId);

    List<JiraBoard> findByConnectionStatusIn(List<IntegrationStatus> statuses);

    List<JiraBoard> findByWebhookExpiresAtBeforeAndConnectionStatusNot(
            LocalDateTime expiresBefore,
            IntegrationStatus excludedStatus
    );

    Optional<JiraBoard> findByWebhookSecretHashAndConnectionStatusNot(
            String webhookSecretHash,
            IntegrationStatus excludedStatus
    );
}
