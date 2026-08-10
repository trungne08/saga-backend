package com.saga.be.service;

import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalIntegrationNotificationProducer {

    private final NotificationService notificationService;

    public void jiraLinked(SagaPrincipal principal, UUID mappingId) {
        notifyLinked(principal, NotificationType.JIRA_LINK_SUCCEEDED, "Jira", mappingId);
    }

    public void gitHubLinked(SagaPrincipal principal, UUID mappingId) {
        notifyLinked(principal, NotificationType.GITHUB_LINK_SUCCEEDED, "GitHub", mappingId);
    }

    private void notifyLinked(
            SagaPrincipal principal,
            NotificationType type,
            String provider,
            UUID mappingId
    ) {
        notificationService.createOnceForEvent(
                principal.localProfileId(),
                principal.applicationRole(),
                type,
                provider + " linked",
                "Your " + provider + " account was linked successfully.",
                "personal-link:" + provider + ":" + mappingId
        );
    }
}
