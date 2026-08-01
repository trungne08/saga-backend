package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.dto.response.CsrfTokenResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class AuthControllerTest {

    private final AuthController controller = new AuthController();

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

        AuthMeResponse response = controller.me(principal, new MockHttpServletRequest());

        assertEquals("cognito-subject", response.cognitoSub());
        assertEquals("student@fpt.edu.vn", response.email());
        assertEquals("Student Name", response.fullName());
        assertEquals(ApplicationRole.STUDENT, response.applicationRole());
        assertEquals(localProfileId, response.localProfileId());
        assertEquals(AccountStatus.ACTIVE, response.accountStatus());
        assertEquals(
                List.of(
                        "cognitoSub",
                        "email",
                        "fullName",
                        "applicationRole",
                        "localProfileId",
                        "accountStatus"
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
}
