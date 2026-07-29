package com.saga.be.security;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.exception.StudentCodeConflictException;
import com.saga.be.service.AuthenticatedProfileService;
import com.saga.be.service.AuthenticationAuditService;
import com.saga.be.service.OidcIdentityService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class CognitoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            CognitoAuthenticationSuccessHandler.class
    );

    private final OidcIdentityService identityService;
    private final AuthenticatedProfileService profileService;
    private final AuthenticationAuditService auditService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final URI successRedirectUri;

    public CognitoAuthenticationSuccessHandler(
            OidcIdentityService identityService,
            AuthenticatedProfileService profileService,
            AuthenticationAuditService auditService,
            SecurityContextRepository securityContextRepository,
            SecurityErrorResponseWriter errorResponseWriter,
            @Value("${app.auth.success-redirect-uri}") String successRedirectUri
    ) {
        this.identityService = identityService;
        this.profileService = profileService;
        this.auditService = auditService;
        this.securityContextRepository = securityContextRepository;
        this.errorResponseWriter = errorResponseWriter;
        this.successRedirectUri = requireHttpUri(
                successRedirectUri,
                "AUTH_SUCCESS_REDIRECT_URI"
        );
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        AuthenticatedIdentity identity = null;
        try {
            OidcUser oidcUser = requireOidcUser(authentication);
            identity = identityService.extract(oidcUser);
            AuthenticatedProfile profile = profileService.synchronize(identity);
            auditService.recordSuccessfulLogin(profile, request.getRemoteAddr());
            replaceWithTokenFreeSessionAuthentication(
                    request,
                    response,
                    authentication,
                    profile
            );
            response.sendRedirect(successRedirectUri.toString());
        } catch (StudentCodeConflictException exception) {
            auditService.recordStudentCodeConflict(
                    exception.getCognitoSub(),
                    exception.getLocalProfileId(),
                    request.getRemoteAddr()
            );
            reject(request, response, HttpStatus.CONFLICT, exception.getMessage());
        } catch (IdentityConflictException exception) {
            auditService.recordIdentityConflict(
                    identity == null ? null : identity.cognitoSub(),
                    exception.getReason(),
                    request.getRemoteAddr()
            );
            reject(request, response, HttpStatus.CONFLICT, exception.getMessage());
        } catch (InvalidIdentityException exception) {
            reject(
                    request,
                    response,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    exception.getMessage()
            );
        } catch (IdentityServiceException exception) {
            reject(request, response, HttpStatus.BAD_GATEWAY, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "OIDC login completion failed with {}",
                    exception.getClass().getSimpleName()
            );
            reject(
                    request,
                    response,
                    HttpStatus.BAD_GATEWAY,
                    "Authentication could not be completed"
            );
        }
    }

    private OidcUser requireOidcUser(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token
                && oauth2Token.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser;
        }
        throw new InvalidIdentityException("Cognito did not return an OIDC user");
    }

    private void replaceWithTokenFreeSessionAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication originalAuthentication,
            AuthenticatedProfile profile
    ) {
        Set<GrantedAuthority> safeAuthorities = new LinkedHashSet<>();
        for (GrantedAuthority authority : originalAuthentication.getAuthorities()) {
            if (!authority.getAuthority().startsWith("ROLE_")) {
                safeAuthorities.add(new SimpleGrantedAuthority(authority.getAuthority()));
            }
        }
        safeAuthorities.add(new SimpleGrantedAuthority("ROLE_" + profile.role().name()));

        Authentication safeAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                SagaPrincipal.from(profile),
                null,
                safeAuthorities
        );
        SecurityContext safeContext = SecurityContextHolder.createEmptyContext();
        safeContext.setAuthentication(safeAuthentication);
        SecurityContextHolder.setContext(safeContext);
        securityContextRepository.saveContext(safeContext, request, response);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        errorResponseWriter.write(
                request,
                response,
                status.value(),
                status.getReasonPhrase(),
                message
        );
    }

    private URI requireHttpUri(String value, String propertyName) {
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
            throw new IllegalStateException(propertyName + " must be an absolute HTTP(S) URI");
        }
    }
}
