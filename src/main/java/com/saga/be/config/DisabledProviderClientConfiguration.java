package com.saga.be.config;

import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import java.lang.reflect.Proxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DisabledProviderClientConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.integrations.jira",
            name = "enabled",
            havingValue = "false"
    )
    public JiraProviderClient disabledJiraProviderClient() {
        return unavailableClient(JiraProviderClient.class, "Jira");
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.integrations.github",
            name = "enabled",
            havingValue = "false"
    )
    public GitHubProviderClient disabledGitHubProviderClient() {
        return unavailableClient(GitHubProviderClient.class, "GitHub");
    }

    @SuppressWarnings("unchecked")
    private <T> T unavailableClient(Class<T> type, String provider) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "Disabled " + provider
                                    + " provider client";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    throw IntegrationException.notConfigured(provider);
                }
        );
    }
}
