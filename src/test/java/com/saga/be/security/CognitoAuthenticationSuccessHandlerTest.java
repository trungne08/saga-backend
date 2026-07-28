package com.saga.be.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.StudentCodeConflictException;
import com.saga.be.service.AuthenticatedProfileService;
import com.saga.be.service.AuthenticationAuditService;
import com.saga.be.service.OidcIdentityService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class CognitoAuthenticationSuccessHandlerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storesOnlyTokenFreeSagaPrincipalInTheHttpSession() throws Exception {
        OidcIdentityService identityService = mock(OidcIdentityService.class);
        AuthenticatedProfileService profileService = mock(
                AuthenticatedProfileService.class
        );
        AuthenticationAuditService auditService = mock(
                AuthenticationAuditService.class
        );
        SecurityErrorResponseWriter errorWriter = mock(
                SecurityErrorResponseWriter.class
        );
        HttpSessionSecurityContextRepository contextRepository =
                new HttpSessionSecurityContextRepository();
        CognitoAuthenticationSuccessHandler handler =
                new CognitoAuthenticationSuccessHandler(
                        identityService,
                        profileService,
                        auditService,
                        contextRepository,
                        errorWriter,
                        "http://localhost:8080/api/auth/me"
                );

        String rawIdToken = "raw-id-token-that-must-not-reach-the-session";
        Instant issuedAt = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", "student-subject",
                "email", "student@fpt.edu.vn",
                "email_verified", true,
                "name", "Student User"
        );
        OidcIdToken idToken = new OidcIdToken(
                rawIdToken,
                issuedAt,
                issuedAt.plusSeconds(300),
                claims
        );
        OidcUserAuthority oidcAuthority = new OidcUserAuthority(idToken);
        OidcUser oidcUser = new DefaultOidcUser(List.of(oidcAuthority), idToken);
        Authentication originalAuthentication = new OAuth2AuthenticationToken(
                oidcUser,
                List.of(
                        oidcAuthority,
                        new SimpleGrantedAuthority("ROLE_STUDENT"),
                        new SimpleGrantedAuthority("SCOPE_openid")
                ),
                "cognito"
        );

        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                "student-subject",
                "student@fpt.edu.vn",
                "Student User",
                ApplicationRole.STUDENT
        );
        UUID profileId = UUID.randomUUID();
        AuthenticatedProfile profile = new AuthenticatedProfile(
                identity.cognitoSub(),
                identity.email(),
                identity.fullName(),
                identity.role(),
                profileId,
                AccountStatus.PENDING
        );
        when(identityService.extract(oidcUser)).thenReturn(identity);
        when(profileService.synchronize(identity)).thenReturn(profile);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                originalAuthentication
        );

        MockHttpSession session = (MockHttpSession) request.getSession(false);
        assertTrue(session != null && !session.isInvalid());
        SecurityContext storedContext = assertInstanceOf(
                SecurityContext.class,
                session.getAttribute(
                        HttpSessionSecurityContextRepository
                                .SPRING_SECURITY_CONTEXT_KEY
                )
        );
        Authentication storedAuthentication = storedContext.getAuthentication();
        assertInstanceOf(
                UsernamePasswordAuthenticationToken.class,
                storedAuthentication
        );
        SagaPrincipal principal = assertInstanceOf(
                SagaPrincipal.class,
                storedAuthentication.getPrincipal()
        );
        assertEquals(identity.cognitoSub(), principal.cognitoSub());
        assertEquals(profileId, principal.localProfileId());
        assertNull(storedAuthentication.getCredentials());
        assertFalse(storedAuthentication.getPrincipal() instanceof OidcUser);
        assertTrue(storedAuthentication.getAuthorities().stream()
                .allMatch(SimpleGrantedAuthority.class::isInstance));
        assertEquals(
                List.of("OIDC_USER", "SCOPE_openid", "ROLE_STUDENT"),
                storedAuthentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
        assertSame(storedContext, SecurityContextHolder.getContext());
        assertEquals(
                List.of(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                Collections.list(session.getAttributeNames())
        );
        assertFalse(storedContext.toString().contains(rawIdToken));
        assertEquals(
                "http://localhost:8080/api/auth/me",
                response.getRedirectedUrl()
        );
        verify(auditService).recordSuccessfulLogin(profile, "127.0.0.1");
    }

    @Test
    void auditsStudentCodeConflictAndReturns409() throws Exception {
        OidcIdentityService identityService = mock(OidcIdentityService.class);
        AuthenticatedProfileService profileService = mock(
                AuthenticatedProfileService.class
        );
        AuthenticationAuditService auditService = mock(
                AuthenticationAuditService.class
        );
        SecurityErrorResponseWriter errorWriter = mock(
                SecurityErrorResponseWriter.class
        );
        CognitoAuthenticationSuccessHandler handler =
                new CognitoAuthenticationSuccessHandler(
                        identityService,
                        profileService,
                        auditService,
                        new HttpSessionSecurityContextRepository(),
                        errorWriter,
                        "http://localhost:8080/api/auth/me"
                );

        OidcUser oidcUser = mock(OidcUser.class);
        Authentication authentication = new OAuth2AuthenticationToken(
                oidcUser,
                List.of(),
                "cognito"
        );
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                "student-subject",
                "studenthe123456@fpt.edu.vn",
                "Student User",
                ApplicationRole.STUDENT
        );
        UUID profileId = UUID.randomUUID();
        StudentCodeConflictException conflict = new StudentCodeConflictException(
                identity.cognitoSub(),
                profileId
        );
        when(identityService.extract(oidcUser)).thenReturn(identity);
        when(profileService.synchronize(identity)).thenThrow(conflict);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(auditService).recordStudentCodeConflict(
                identity.cognitoSub(),
                profileId,
                "127.0.0.1"
        );
        verify(errorWriter).write(
                request,
                response,
                409,
                "Conflict",
                conflict.getMessage()
        );
    }
}
