package com.saga.be.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CognitoLogoutSuccessHandler implements LogoutSuccessHandler {

    private final ClientRegistrationRepository registrations;
    private final SecurityErrorResponseWriter responseWriter;
    private final URI logoutRedirectUri;
    private final URI configuredCognitoDomain;

    public CognitoLogoutSuccessHandler(
            ClientRegistrationRepository registrations,
            SecurityErrorResponseWriter responseWriter,
            @Value("${app.auth.logout-redirect-uri}") String logoutRedirectUri,
            @Value("${app.auth.cognito-domain:}") String cognitoDomain
    ) {
        this.registrations = registrations;
        this.responseWriter = responseWriter;
        this.logoutRedirectUri = requireHttpUri(logoutRedirectUri);
        this.configuredCognitoDomain = cognitoDomain == null || cognitoDomain.isBlank()
                ? null
                : requireHttpsDomain(cognitoDomain);
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        try {
            ClientRegistration registration = registrations.findByRegistrationId("cognito");
            if (registration == null) {
                throw new IllegalStateException("Cognito client registration is unavailable");
            }

            URI cognitoDomain = configuredCognitoDomain == null
                    ? domainFromAuthorizationUri(registration)
                    : configuredCognitoDomain;
            String target = UriComponentsBuilder.fromUri(cognitoDomain)
                    .path("/logout")
                    .queryParam("client_id", registration.getClientId())
                    .queryParam("logout_uri", logoutRedirectUri.toString())
                    .build()
                    .encode()
                    .toUriString();
            response.sendRedirect(target);
        } catch (RuntimeException exception) {
            responseWriter.write(
                    request,
                    response,
                    HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                    "Cognito logout could not be initiated"
            );
        }
    }

    private URI requireHttpUri(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "AUTH_LOGOUT_REDIRECT_URI must be an absolute HTTP(S) URI"
            );
        }
    }

    private URI requireHttpsDomain(String value) {
        URI uri = requireHttpUri(value);
        boolean noPath = uri.getPath() == null
                || uri.getPath().isEmpty()
                || "/".equals(uri.getPath());
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !noPath
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "COGNITO_DOMAIN must be an HTTPS origin without a path"
            );
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private URI domainFromAuthorizationUri(ClientRegistration registration) {
        URI authorizationUri = URI.create(
                registration.getProviderDetails().getAuthorizationUri()
        );
        if (!"https".equalsIgnoreCase(authorizationUri.getScheme())
                || authorizationUri.getAuthority() == null) {
            throw new IllegalStateException("Cognito authorization endpoint is invalid");
        }
        return URI.create(
                authorizationUri.getScheme() + "://" + authorizationUri.getAuthority()
        );
    }
}
