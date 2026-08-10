package com.saga.be.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.entity.Notification;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.FirebaseInstallationRepository;
import com.saga.be.repository.NotificationDeliveryRepository;
import com.saga.be.repository.NotificationRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyNotificationAndFirebaseInstallationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private FirebaseInstallationRepository installationRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @Autowired private NotificationService notificationService;

    @BeforeEach
    void cleanNotificationTables() {
        deliveryRepository.deleteAll();
        installationRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void listAndUnreadCountAreOwnerScopedAndNewestFirst() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Notification older = notification(ownerId, ApplicationRole.STUDENT, "older");
        older.setCreatedAt(LocalDateTime.of(2026, 8, 10, 10, 0));
        notificationRepository.save(older);
        Notification newer = notification(ownerId, ApplicationRole.STUDENT, "newer");
        newer.setCreatedAt(LocalDateTime.of(2026, 8, 10, 11, 0));
        notificationRepository.save(newer);
        notificationRepository.save(notification(UUID.randomUUID(), ApplicationRole.STUDENT, "foreign"));
        notificationRepository.save(notification(ownerId, ApplicationRole.LECTURER, "other-role"));

        mockMvc.perform(get("/api/me/notifications?page=0&size=10")
                        .with(authentication(authenticationFor(ownerId, ApplicationRole.STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("newer"))
                .andExpect(jsonPath("$.content[1].title").value("older"));

        mockMvc.perform(get("/api/me/notifications/unread-count")
                        .with(authentication(authenticationFor(ownerId, ApplicationRole.STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void markReadIsOwnerOnlyIdempotentAndRequiresCsrf() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Notification owned = notificationRepository.save(notification(ownerId, ApplicationRole.STUDENT, "owned"));

        mockMvc.perform(patch("/api/me/notifications/{id}/read", owned.getId())
                        .with(authentication(authenticationFor(ownerId, ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/me/notifications/{id}/read", owned.getId())
                        .with(authentication(authenticationFor(UUID.randomUUID(), ApplicationRole.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/me/notifications/{id}/read", owned.getId())
                        .with(authentication(authenticationFor(ownerId, ApplicationRole.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        LocalDateTime firstReadAt = notificationRepository.findById(owned.getId()).orElseThrow().getReadAt();
        mockMvc.perform(patch("/api/me/notifications/{id}/read", owned.getId())
                        .with(authentication(authenticationFor(ownerId, ApplicationRole.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(
                notificationRepository.findById(owned.getId()).orElseThrow().getReadAt()
        ).isEqualTo(firstReadAt);
    }

    @Test
    void registrationIsOwnedIdempotentAndRevocable() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Authentication owner = authenticationFor(ownerId, ApplicationRole.STUDENT);
        String body = "{\"firebaseInstallationId\":\"fid-browser-one\"}";

        register(owner, body).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        register(owner, body).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(installationRepository.count()).isOne();

        FirebaseInstallation installation = installationRepository.findAll().get(0);
        mockMvc.perform(delete("/api/me/firebase-installations/{id}", installation.getId())
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(delete("/api/me/firebase-installations/{id}", installation.getId())
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void foreignFidConflictsAndSecurityUsesSessionAndCsrf() throws Exception {
        String body = "{\"firebaseInstallationId\":\"fid-shared\"}";
        register(authenticationFor(UUID.randomUUID(), ApplicationRole.STUDENT), body)
                .andExpect(status().isOk());
        register(authenticationFor(UUID.randomUUID(), ApplicationRole.STUDENT), body)
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/me/firebase-installations")
                        .with(authentication(authenticationFor(UUID.randomUUID(), ApplicationRole.STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firebaseInstallationId\":\"fid-no-csrf\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", "Bearer not-supported"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankInstallationIdIsRejected() throws Exception {
        register(authenticationFor(UUID.randomUUID(), ApplicationRole.STUDENT),
                "{\"firebaseInstallationId\":\"   \"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void notificationAndPerInstallationDeliveryArePersistedTogether() {
        UUID ownerId = UUID.randomUUID();
        installationRepository.save(FirebaseInstallation.builder()
                .ownerProfileId(ownerId)
                .ownerRole(ApplicationRole.STUDENT)
                .firebaseInstallationId("fid-durable-outbox")
                .active(true)
                .lastRegisteredAt(LocalDateTime.now())
                .build());

        Notification created = notificationService.create(
                ownerId,
                ApplicationRole.STUDENT,
                NotificationType.COURSE_MEMBERSHIP_ADDED,
                "created",
                "durable delivery",
                null
        );

        org.assertj.core.api.Assertions.assertThat(notificationRepository.findById(created.getId()))
                .isPresent();
        org.assertj.core.api.Assertions.assertThat(deliveryRepository.findAll())
                .singleElement()
                .satisfies(delivery -> {
                    org.assertj.core.api.Assertions.assertThat(delivery.getNotification().getId())
                            .isEqualTo(created.getId());
                    org.assertj.core.api.Assertions.assertThat(delivery.getDeliveryStatus().name())
                            .isEqualTo("PENDING");
                    org.assertj.core.api.Assertions.assertThat(delivery.getAttemptCount()).isZero();
                });
    }

    private org.springframework.test.web.servlet.ResultActions register(Authentication owner, String body)
            throws Exception {
        return mockMvc.perform(post("/api/me/firebase-installations")
                .with(authentication(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Notification notification(UUID ownerId, ApplicationRole role, String title) {
        return Notification.builder()
                .recipientProfileId(ownerId)
                .recipientRole(role)
                .notificationType(NotificationType.COURSE_MEMBERSHIP_ADDED)
                .title(title)
                .message("test message")
                .build();
    }

    private Authentication authenticationFor(UUID profileId, ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-notification-subject",
                role.name().toLowerCase() + "@test.invalid",
                role.name(), role, profileId, AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
