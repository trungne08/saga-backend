package com.saga.be.service;

import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectIntegrationNotificationProducer {

    private final NotificationService notificationService;

    public void jiraProjectLinked(SagaPrincipal principal, UUID projectId, UUID boardId) {
        notifyLinked(principal, projectId, "Jira", NotificationType.JIRA_PROJECT_LINK_SUCCEEDED, boardId);
    }

    public void gitHubProjectLinked(SagaPrincipal principal, UUID projectId, long installationId) {
        notifyLinked(
                principal,
                projectId,
                "GitHub",
                NotificationType.GITHUB_PROJECT_LINK_SUCCEEDED,
                Long.toString(installationId)
        );
    }

    private void notifyLinked(
            SagaPrincipal principal,
            UUID projectId,
            String provider,
            NotificationType type,
            Object stableLinkId
    ) {
        notificationService.createOnceForEvent(
                principal.localProfileId(),
                principal.applicationRole(),
                type,
                provider + " project linked",
                "A " + provider + " project integration was linked successfully.",
                "project-link:" + provider + ":" + projectId + ":" + stableLinkId
        );
    }
}
