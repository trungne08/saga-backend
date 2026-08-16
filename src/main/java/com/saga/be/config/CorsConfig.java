package com.saga.be.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String configuredOrigins
    ) {
        List<String> allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(this::requireExplicitOrigin)
                .distinct()
                .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "FRONTEND_ORIGINS must contain at least one explicit origin"
            );
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "X-XSRF-TOKEN",
                        "Accept",
                        "Idempotency-Key",
                        "Last-Event-ID"
                )
        );
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private String requireExplicitOrigin(String configuredOrigin) {
        if (configuredOrigin.contains("*")) {
            throw new IllegalStateException("FRONTEND_ORIGINS cannot contain wildcards");
        }

        try {
            URI origin = URI.create(configuredOrigin);
            boolean validScheme = "http".equalsIgnoreCase(origin.getScheme())
                    || "https".equalsIgnoreCase(origin.getScheme());
            boolean noPath = origin.getPath() == null
                    || origin.getPath().isEmpty()
                    || "/".equals(origin.getPath());
            if (!validScheme
                    || origin.getHost() == null
                    || origin.getUserInfo() != null
                    || origin.getQuery() != null
                    || origin.getFragment() != null
                    || !noPath) {
                throw new IllegalArgumentException();
            }
            return origin.getScheme() + "://" + origin.getAuthority();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "FRONTEND_ORIGINS contains an invalid origin"
            );
        }
    }
}
