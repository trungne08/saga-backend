package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.CognitoRoleResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OidcIdentityServiceTest {

    private final OidcIdentityService identityService = new OidcIdentityService(
            new CognitoRoleResolver()
    );

    @Test
    void acceptsBooleanTrueFromTheIdToken() {
        AuthenticatedIdentity identity = identityService.extract(user(true, null));

        assertEquals(ApplicationRole.STUDENT, identity.role());
    }

    @Test
    void acceptsLowercaseTrueStringFromUserInfoWhenIdTokenClaimIsAbsent() {
        AuthenticatedIdentity identity = identityService.extract(user(null, "true"));

        assertEquals(ApplicationRole.STUDENT, identity.role());
    }

    @Test
    void acceptsUppercaseTrueStringFromUserInfoWhenIdTokenClaimIsAbsent() {
        AuthenticatedIdentity identity = identityService.extract(user(null, "TRUE"));

        assertEquals(ApplicationRole.STUDENT, identity.role());
    }

    @Test
    void rejectsBooleanFalseFromTheIdToken() {
        assertUnverified(user(false, "true"));
    }

    @Test
    void rejectsFalseStringFromUserInfo() {
        assertUnverified(user(null, "false"));
    }

    @Test
    void rejectsMissingEmailVerifiedClaim() {
        assertUnverified(user(null, null));
    }

    private void assertUnverified(OidcUser user) {
        InvalidIdentityException exception = assertThrows(
                InvalidIdentityException.class,
                () -> identityService.extract(user)
        );
        assertEquals("A verified email is required", exception.getMessage());
    }

    private OidcUser user(Object idTokenEmailVerified, Object userInfoEmailVerified) {
        Map<String, Object> idTokenClaims = new java.util.HashMap<>();
        idTokenClaims.put("sub", "cognito-subject");
        idTokenClaims.put("email", "student@fpt.edu.vn");
        idTokenClaims.put("name", "Student User");
        idTokenClaims.put("cognito:groups", List.of("STUDENT"));
        if (idTokenEmailVerified != null) {
            idTokenClaims.put("email_verified", idTokenEmailVerified);
        }

        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                idTokenClaims
        );

        OidcUserInfo userInfo = null;
        if (userInfoEmailVerified != null) {
            userInfo = new OidcUserInfo(Map.of(
                    "email_verified",
                    userInfoEmailVerified
            ));
        }

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                idToken,
                userInfo
        );
    }
}
