package com.saga.be.service;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.CognitoRoleResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class OidcIdentityService {

    private final CognitoRoleResolver roleResolver;
    private final StudentIdentityNormalizer identityNormalizer;

    public OidcIdentityService(
            CognitoRoleResolver roleResolver,
            StudentIdentityNormalizer identityNormalizer
    ) {
        this.roleResolver = roleResolver;
        this.identityNormalizer = identityNormalizer;
    }

    public AuthenticatedIdentity extract(OidcUser user) {
        if (user == null) {
            throw new InvalidIdentityException("Cognito did not return an OIDC user");
        }

        String subject = requireText(user.getSubject(), "Cognito subject is missing");
        String email = identityNormalizer.normalizeEmail(
                requireText(user.getEmail(), "A verified email is required")
        );
        if (!isEmailVerified(user)) {
            throw new InvalidIdentityException("A verified email is required");
        }

        String fullName = requireText(
                user.getClaimAsString("name"),
                "The Cognito name attribute is required"
        );
        ApplicationRole role = roleResolver
                .resolve(user.getClaims().get("cognito:groups"))
                .orElseThrow(() -> new InvalidIdentityException(
                        "No supported Cognito application group was assigned"
                ));

        return new AuthenticatedIdentity(subject, email, fullName, role);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdentityException(message);
        }
        return value.trim();
    }

    private boolean isEmailVerified(OidcUser user) {
        Object value = user.getIdToken() == null
                ? null
                : user.getIdToken().getClaim("email_verified");
        if (value == null) {
            value = user.getClaims().get("email_verified");
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return "true".equalsIgnoreCase(stringValue.trim());
        }
        return false;
    }
}
