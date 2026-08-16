package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.AccountSessionEventHub;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AdminUserStatusService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountSessionEventsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private AccountSessionEventHub hub;
    @Autowired private AdminUserStatusService adminUserStatusService;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        try {
            hub.completeAllAndClear();
        } catch (RuntimeException ignored) {
            // Completing a MockMvc async emitter can dispatch into an already-finished request.
        }
        SecurityContextHolder.clearContext();
        studentRepository.findAll().stream()
                .filter(student -> student.getEmail() != null && student.getEmail().endsWith("@session-events.test"))
                .forEach(studentRepository::delete);
        lecturerRepository.findAll().stream()
                .filter(lecturer -> lecturer.getEmail() != null && lecturer.getEmail().endsWith("@session-events.test"))
                .forEach(lecturerRepository::delete);
        studentRepository.flush();
        lecturerRepository.flush();
    }

    @Test
    void anonymousAndBearerRequestsCannotOpenTheSessionEventStream() throws Exception {
        mockMvc.perform(get("/api/auth/session-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/auth/session-events").header("Authorization", "Bearer not-a-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"));
        assertEquals(0, hub.activeSubscriptionCount());
    }

    @Test
    void activeStudentAndLecturerCanOpenTheStreamWithoutCsrfOrClientIdentity() throws Exception {
        Student student = student("stream-student", AccountStatus.ACTIVE);
        Lecturer lecturer = lecturer("stream-lecturer", AccountStatus.ACTIVE);
        MockHttpSession studentSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        MockHttpSession lecturerSession = session(authenticationFor(
                ApplicationRole.LECTURER, lecturer.getId(), AccountStatus.ACTIVE
        ));

        startStream(studentSession);
        hub.completeAllAndClear();
        startStream(lecturerSession);

        assertEquals(1, hub.activeSubscriptionCount());
        assertFalse(studentSession.isInvalid());
        assertFalse(lecturerSession.isInvalid());
    }

    @Test
    void disabledAccountCannotSubscribeAndExistingDec101GatesRemain() throws Exception {
        Student student = student("disabled-stream", AccountStatus.INACTIVE);
        MockHttpSession streamSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/auth/session-events").session(streamSession)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(streamSession.isInvalid());
        assertEquals(0, hub.activeSubscriptionCount());

        MockHttpSession meSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/auth/me").session(meSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(meSession.isInvalid());

        MockHttpSession businessSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/v1/subjects").session(businessSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(businessSession.isInvalid());

        MockHttpSession patchSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(patch("/api/auth/me").session(patchSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Blocked\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(patchSession.isInvalid());
    }

    @Test
    void adminDisablePushesAccountDisabledSideEffectAndInvalidatesTheStudentSession() throws Exception {
        Student student = student("disable-student", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession session = session(authentication);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), session);

        disableAfterCommit(student.getId(), AccountStatus.INACTIVE);

        assertTrue(session.isInvalid());
        assertEquals(0, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.getId()));
        mockMvc.perform(get("/api/v1/subjects").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminDisableInvalidatesTheLecturerSessionWithoutRestoringItOnReEnable() throws Exception {
        Lecturer lecturer = lecturer("disable-lecturer", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(
                ApplicationRole.LECTURER, lecturer.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession session = session(authentication);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), session);

        disableAfterCommit(lecturer.getId(), AccountStatus.SUSPENDED);

        assertTrue(session.isInvalid());
        assertEquals(0, hub.activeSubscriptionCount(ApplicationRole.LECTURER, lecturer.getId()));
        mockMvc.perform(get("/api/v1/subjects").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void multipleTabsAndIndependentSessionsForTheSameProfileAreAllRevoked() throws Exception {
        Student student = student("multi-session", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession first = session(authentication);
        MockHttpSession second = session(authentication);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), first);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), second);
        assertEquals(2, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.getId()));

        disableAfterCommit(student.getId(), AccountStatus.INACTIVE);

        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
        assertEquals(0, hub.activeSubscriptionCount());
    }

    @Test
    void rolledBackStatusMutationDoesNotEmitADisableEvent() throws Exception {
        Student student = student("rollback-stream", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession session = session(authentication);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), session);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adminUserStatusService.updateStatus(adminPrincipal(), student.getId(), AccountStatus.INACTIVE);
            status.setRollbackOnly();
        });

        assertEquals(AccountStatus.ACTIVE, studentRepository.findById(student.getId()).orElseThrow().getAccountStatus());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.getId()));
        assertFalse(session.isInvalid());
    }

    @Test
    void missedLocalEventIsDetectedByServerRevalidationWithoutFalseLogoutOfActiveProfiles() throws Exception {
        Student disabled = student("missed-event", AccountStatus.ACTIVE);
        Student active = student("still-active", AccountStatus.ACTIVE);
        Authentication disabledAuth = authenticationFor(
                ApplicationRole.STUDENT, disabled.getId(), AccountStatus.ACTIVE
        );
        Authentication activeAuth = authenticationFor(
                ApplicationRole.STUDENT, active.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession disabledSession = session(disabledAuth);
        MockHttpSession activeSession = session(activeAuth);
        hub.subscribe((SagaPrincipal) disabledAuth.getPrincipal(), disabledSession);
        hub.subscribe((SagaPrincipal) activeAuth.getPrincipal(), activeSession);

        disabled.setAccountStatus(AccountStatus.SUSPENDED);
        studentRepository.saveAndFlush(disabled);

        hub.revalidateConnectedProfiles();

        assertTrue(disabledSession.isInvalid());
        assertFalse(activeSession.isInvalid());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, active.getId()));
        assertEquals(0, hub.activeSubscriptionCount(ApplicationRole.STUDENT, disabled.getId()));
    }

    @Test
    void reEnableDoesNotResurrectTheInvalidatedSession() throws Exception {
        Student student = student("reenable-stream", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession oldSession = session(authentication);
        hub.subscribe((SagaPrincipal) authentication.getPrincipal(), oldSession);
        disableAfterCommit(student.getId(), AccountStatus.INACTIVE);
        assertTrue(oldSession.isInvalid());

        disableAfterCommit(student.getId(), AccountStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/subjects").session(oldSession)).andExpect(status().isUnauthorized());
        assertEquals(0, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.getId()));

        MockHttpSession newSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/v1/subjects").session(newSession)).andExpect(status().isOk());
        hub.subscribe((SagaPrincipal) authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ).getPrincipal(), newSession);
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.getId()));
        assertFalse(newSession.isInvalid());
    }

    @Test
    void sessionEventStreamIncludesCorsCredentialsForTheConfiguredFrontend() throws Exception {
        Student student = student("cors-stream", AccountStatus.ACTIVE);
        MockHttpSession session = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/auth/session-events")
                        .session(session)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Origin", "http://localhost:3000"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void adminStreamStaysHeartbeatOnlyWhenAnotherProfileIsDisabled() throws Exception {
        Student student = student("admin-heartbeat", AccountStatus.ACTIVE);
        Authentication admin = adminAuthentication();
        Authentication studentAuth = authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        );
        MockHttpSession adminSession = session(admin);
        MockHttpSession studentSession = session(studentAuth);
        hub.subscribe((SagaPrincipal) admin.getPrincipal(), adminSession);
        hub.subscribe((SagaPrincipal) studentAuth.getPrincipal(), studentSession);

        disableAfterCommit(student.getId(), AccountStatus.INACTIVE);

        assertFalse(adminSession.isInvalid());
        assertTrue(studentSession.isInvalid());
        assertEquals(1, hub.activeSubscriptionCount());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.ADMIN, ((SagaPrincipal) admin.getPrincipal()).localProfileId()));
    }

    private MvcResult startStream(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/auth/session-events")
                        .session(session)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void disableAfterCommit(UUID profileId, AccountStatus status) {
        adminUserStatusService.updateStatus(adminPrincipal(), profileId, status);
    }

    private MockHttpSession session(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Student student(String suffix, AccountStatus status) {
        return studentRepository.saveAndFlush(Student.builder().cognitoSub(suffix + "-sub")
                .studentCode("SE" + Math.abs(suffix.hashCode() % 1_000_000))
                .email(suffix + "@session-events.test").fullName(suffix).accountStatus(status).build());
    }

    private Lecturer lecturer(String suffix, AccountStatus status) {
        return lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub(suffix + "-sub")
                .email(suffix + "@session-events.test").fullName(suffix).accountStatus(status).build());
    }

    private SagaPrincipal adminPrincipal() {
        return (SagaPrincipal) adminAuthentication().getPrincipal();
    }

    private Authentication adminAuthentication() {
        return authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID(), null);
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId, AccountStatus status) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-sub", role.name().toLowerCase()
                + "@session-events.test", role.name(), role, localProfileId, status);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
