package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.dto.response.CsrfTokenResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.security.AccountSessionEventHub;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CurrentAccountStatusService;
import com.saga.be.service.SelfProfileService;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AuthControllerTest {

    private final CurrentAccountStatusService accountStatusService = mock(CurrentAccountStatusService.class);
    private final SelfProfileService selfProfileService = mock(SelfProfileService.class);
    private final AccountSessionEventHub accountSessionEventHub = mock(AccountSessionEventHub.class);
    private final AuthController controller = new AuthController(
            accountStatusService,
            selfProfileService,
            accountSessionEventHub
    );

    @Test
    void loginRedirectsToTheBackendCognitoAuthorizationEndpoint() {
        var response = controller.login();

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(
                URI.create("/oauth2/authorization/cognito"),
                response.getHeaders().getLocation()
        );
        assertNull(response.getBody());
    }

    @Test
    void meReturnsOnlyTheSafeLocalSessionProfile() {
        UUID localProfileId = UUID.randomUUID();
        SagaPrincipal principal = new SagaPrincipal(
                "cognito-subject",
                "student@fpt.edu.vn",
                "Student Name",
                ApplicationRole.STUDENT,
                localProfileId,
                AccountStatus.ACTIVE
        );

        when(accountStatusService.currentStatusForAuthRoute(principal)).thenReturn(AccountStatus.ACTIVE);
        when(selfProfileService.read(principal)).thenReturn(AuthMeResponse.from(principal, AccountStatus.ACTIVE));
        AuthMeResponse response = controller.me(principal, new MockHttpServletRequest());

        assertEquals("cognito-subject", response.cognitoSub());
        assertEquals("student@fpt.edu.vn", response.email());
        assertEquals("Student Name", response.fullName());
        assertEquals(ApplicationRole.STUDENT, response.applicationRole());
        assertEquals(localProfileId, response.localProfileId());
        assertEquals(AccountStatus.ACTIVE, response.accountStatus());
        assertNull(response.avatarUrl());
        assertNull(response.studentCode());
        assertEquals(
                List.of(
                        "cognitoSub",
                        "email",
                        "fullName",
                        "applicationRole",
                        "localProfileId",
                        "accountStatus",
                        "avatarUrl",
                        "studentCode"
                ),
                Arrays.stream(AuthMeResponse.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList()
        );
    }

    @Test
    void meRejectsAMissingSessionPrincipal() {
        assertThrows(
                UnauthenticatedRequestException.class,
                () -> controller.me(null, new MockHttpServletRequest())
        );
    }

    @Test
    void meReturnsSynchronizedAvatarUrlFromTheSessionPrincipal() {
        UUID localProfileId = UUID.randomUUID();
        SagaPrincipal principal = new SagaPrincipal(
                "cognito-subject",
                "student@fpt.edu.vn",
                "Student Name",
                ApplicationRole.STUDENT,
                localProfileId,
                AccountStatus.ACTIVE,
                "https://cdn.example.test/student.png"
        );
        when(accountStatusService.currentStatusForAuthRoute(principal)).thenReturn(AccountStatus.ACTIVE);
        when(selfProfileService.read(principal)).thenReturn(AuthMeResponse.from(principal, AccountStatus.ACTIVE));

        AuthMeResponse response = controller.me(principal, new MockHttpServletRequest());

        assertEquals("https://cdn.example.test/student.png", response.avatarUrl());
        assertEquals(localProfileId, response.localProfileId());
        assertEquals(AccountStatus.ACTIVE, response.accountStatus());
    }

    @Test
    void csrfReturnsOnlyTheSpringSecurityCsrfContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                CsrfToken.class.getName(),
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-value")
        );
        SagaPrincipal principal = new SagaPrincipal(
                "cognito-subject",
                "student@fpt.edu.vn",
                "Student Name",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );

        CsrfTokenResponse response = controller.csrf(principal, request);

        assertEquals("csrf-value", response.token());
        assertEquals("X-XSRF-TOKEN", response.headerName());
        assertEquals("_csrf", response.parameterName());
        assertFalse(response.toString().contains("csrf-value"));
        assertEquals(
                List.of("token", "headerName", "parameterName"),
                Arrays.stream(CsrfTokenResponse.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList()
        );
    }

    @Test
    void sessionEventsRejectsAMissingSessionPrincipal() {
        assertThrows(
                UnauthenticatedRequestException.class,
                () -> controller.sessionEvents(
                        null,
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
        );
    }

    @Test
    void sessionEventsSubscribesTheCurrentSessionWithoutClientIdentityInput() {
        SagaPrincipal principal = new SagaPrincipal(
                "cognito-subject",
                "student@fpt.edu.vn",
                "Student Name",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        SseEmitter emitter = new SseEmitter(1_000L);
        when(accountSessionEventHub.subscribe(principal, session)).thenReturn(emitter);

        SseEmitter response = controller.sessionEvents(principal, request, new MockHttpServletResponse());

        assertSame(emitter, response);
        verify(accountSessionEventHub).subscribe(principal, session);
    }
}
