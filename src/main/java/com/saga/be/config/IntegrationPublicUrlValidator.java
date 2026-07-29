package com.saga.be.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class IntegrationPublicUrlValidator {

    private final String publicBaseUrl;
    private final JiraIntegrationProperties jiraProperties;
    private final GitHubIntegrationProperties gitHubProperties;
    private final boolean allowInsecureLocalUrl;

    public IntegrationPublicUrlValidator(
            @Value("${app.public-base-url}") String publicBaseUrl,
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties,
            Environment environment
    ) {
        this.publicBaseUrl = publicBaseUrl;
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
        this.allowInsecureLocalUrl = environment.acceptsProfiles(
                Profiles.of("local", "test")
        );
    }

    @PostConstruct
    public void validate() {
        URI base = absoluteUrl("PUBLIC_BASE_URL", publicBaseUrl);
        if (
            base.getUserInfo() != null
            || base.getQuery() != null
            || base.getFragment() != null
            || (
                base.getPath() != null
                && !base.getPath().isBlank()
                && !"/".equals(base.getPath())
            )
        ) {
            throw invalid("PUBLIC_BASE_URL must be an origin without a path");
        }
        if (
            !"https".equalsIgnoreCase(base.getScheme())
            && !(
                allowInsecureLocalUrl
                && "http".equalsIgnoreCase(base.getScheme())
            )
        ) {
            throw invalid("PUBLIC_BASE_URL must use HTTPS");
        }

        String origin = base.getScheme()
                + "://"
                + base.getHost()
                + (base.getPort() < 0 ? "" : ":" + base.getPort());
        requireDerived(
                "JIRA_CALLBACK_URL",
                jiraProperties.callbackUrl(),
                origin + "/api/integrations/jira/callback"
        );
        requireDerived(
                "JIRA_WEBHOOK_PUBLIC_URL",
                jiraProperties.webhookPublicUrl(),
                origin + "/api/webhooks/jira"
        );
        requireDerived(
                "GITHUB_PERSONAL_CALLBACK_URL",
                gitHubProperties.personalCallbackUrl(),
                origin + "/api/me/integrations/github/callback"
        );
        requireDerived(
                "GITHUB_PROJECT_CALLBACK_URL",
                gitHubProperties.projectCallbackUrl(),
                origin + "/api/integrations/github/project/callback"
        );
        requireDerived(
                "GITHUB_SETUP_URL",
                gitHubProperties.setupUrl(),
                origin + "/api/integrations/github/setup"
        );
    }

    private void requireDerived(
            String variable,
            String configured,
            String expected
    ) {
        URI actual = absoluteUrl(variable, configured);
        URI expectedUri = URI.create(expected);
        if (!expectedUri.equals(actual)) {
            throw invalid(variable + " must equal " + expected);
        }
    }

    private URI absoluteUrl(String variable, String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw invalid(variable + " must be an absolute URL");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(variable + " is not a valid URL");
        }
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "Invalid public integration URL configuration: " + message
        );
    }
}
