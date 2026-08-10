package com.saga.be.service;

import static org.mockito.Mockito.verify;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectIntegrationNotificationProducerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private ProjectIntegrationNotificationProducer producer;

    @Test
    void jiraBoardLinkNotifiesOnlyTheInitiatingActorWithStableEventKey() {
        SagaPrincipal principal = principal();
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        producer.jiraProjectLinked(principal, projectId, boardId);

        verify(notificationService).createOnceForEvent(
                principal.localProfileId(),
                ApplicationRole.STUDENT,
                NotificationType.JIRA_PROJECT_LINK_SUCCEEDED,
                "Jira project linked",
                "A Jira project integration was linked successfully.",
                "project-link:Jira:" + projectId + ":" + boardId
        );
    }

    @Test
    void gitHubInstallationLinkNotifiesOnlyTheInitiatingActorWithStableEventKey() {
        SagaPrincipal principal = principal();
        UUID projectId = UUID.randomUUID();

        producer.gitHubProjectLinked(principal, projectId, 42L);

        verify(notificationService).createOnceForEvent(
                principal.localProfileId(),
                ApplicationRole.STUDENT,
                NotificationType.GITHUB_PROJECT_LINK_SUCCEEDED,
                "GitHub project linked",
                "A GitHub project integration was linked successfully.",
                "project-link:GitHub:" + projectId + ":42"
        );
    }

    private SagaPrincipal principal() {
        return new SagaPrincipal(
                "student-sub",
                "student@example.test",
                "Student",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
    }
}
