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
    private final IntegrationUrlResolver urlResolver;
    private final boolean allowInsecureLocalUrl;

    public IntegrationPublicUrlValidator(
            @Value("${app.public-base-url}") String publicBaseUrl,
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties,
            IntegrationUrlResolver urlResolver,
            Environment environment
    ) {
        this.publicBaseUrl = publicBaseUrl;
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
        this.urlResolver = urlResolver;
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
        if (jiraProperties.enabled()) {
            requireConfigured(jiraProperties.clientId(), "JIRA_CLIENT_ID");
            requireConfigured(jiraProperties.clientSecret(), "JIRA_CLIENT_SECRET");
            requireConfigured(
                    jiraProperties.authorizationUrl(),
                    "JIRA_AUTHORIZATION_URL"
            );
            requireConfigured(jiraProperties.tokenUrl(), "JIRA_TOKEN_URL");
            requireConfigured(jiraProperties.apiBaseUrl(), "JIRA_API_BASE_URL");
            requireConfigured(jiraProperties.scopes(), "JIRA_SCOPES");
            requireDerived(
                    "JIRA_CALLBACK_URL",
                    jiraProperties.callbackUrl(),
                    origin + "/api/integrations/jira/callback"
            );
            requireWebhookUrl(
                    "JIRA_WEBHOOK_PUBLIC_URL",
                    urlResolver.jiraWebhookPublicUrl(),
                    origin + "/api/webhooks/jira"
            );
        }
        if (gitHubProperties.enabled()) {
            requireConfigured(gitHubProperties.appId(), "GITHUB_APP_ID");
            requireConfigured(gitHubProperties.clientId(), "GITHUB_CLIENT_ID");
            requireConfigured(
                    gitHubProperties.clientSecret(),
                    "GITHUB_CLIENT_SECRET"
            );
            requireConfigured(
                    gitHubProperties.privateKey(),
                    "GITHUB_PRIVATE_KEY"
            );
            requireConfigured(
                    gitHubProperties.webhookSecret(),
                    "GITHUB_WEBHOOK_SECRET"
            );
            requireConfigured(gitHubProperties.appSlug(), "GITHUB_APP_SLUG");
            requireConfigured(
                    gitHubProperties.apiBaseUrl(),
                    "GITHUB_API_BASE_URL"
            );
            requireConfigured(
                    gitHubProperties.webBaseUrl(),
                    "GITHUB_WEB_BASE_URL"
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
            requireWebhookUrl(
                    "GITHUB_WEBHOOK_PUBLIC_URL",
                    urlResolver.gitHubWebhookPublicUrl(),
                    origin + "/api/webhooks/github"
            );
        }
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

    private void requireWebhookUrl(
            String variable,
            String configured,
            String productionExpected
    ) {
        requireConfigured(configured, variable);
        URI actual = absoluteUrl(variable, configured);
        if (!"https".equalsIgnoreCase(actual.getScheme())) {
            throw invalid(variable + " must use HTTPS");
        }
        if (!allowInsecureLocalUrl
                && !URI.create(productionExpected).equals(actual)) {
            throw invalid(variable + " must equal " + productionExpected);
        }
    }

    private void requireConfigured(String value, String variable) {
        if (value == null || value.isBlank() || placeholder(value)) {
            throw invalid(variable + " must be configured when enabled");
        }
    }

    private boolean placeholder(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("value")
                || normalized.equals("changeme")
                || normalized.equals("example-secret")
                || (normalized.startsWith("<") && normalized.endsWith(">"))
                || normalized.contains("${{")
                || normalized.contains("{{");
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
