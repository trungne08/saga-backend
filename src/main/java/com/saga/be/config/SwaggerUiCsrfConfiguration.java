package com.saga.be.config;

import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
public class SwaggerUiCsrfConfiguration {

    @Bean
    @ConditionalOnMissingBean(SwaggerIndexTransformer.class)
    SwaggerIndexTransformer safeMethodCsrfSwaggerIndexTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {
        return new SafeMethodCsrfSwaggerIndexTransformer(
                swaggerUiConfig,
                swaggerUiOAuthProperties,
                swaggerWelcomeCommon,
                objectMapperProvider
        );
    }

    private static final class SafeMethodCsrfSwaggerIndexTransformer
            extends SwaggerIndexPageTransformer {

        private final String cookieName;
        private final String headerName;

        private SafeMethodCsrfSwaggerIndexTransformer(
                SwaggerUiConfigProperties swaggerUiConfig,
                SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                SwaggerWelcomeCommon swaggerWelcomeCommon,
                ObjectMapperProvider objectMapperProvider
        ) {
            super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
            this.cookieName = swaggerUiConfig.getCsrf().getCookieName();
            this.headerName = swaggerUiConfig.getCsrf().getHeaderName();
        }

        @Override
        protected String addCSRF(String swaggerInitializer) {
            String interceptor = """
                    requestInterceptor: async (request) => {
                      const unsafeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
                      if (!unsafeMethods.has((request.method || 'GET').toUpperCase())) return request;
                      const requestUrl = new URL(request.url, window.location.origin);
                      if (requestUrl.origin !== window.location.origin) return request;
                      const prefix = '%s=';
                      const readCsrfCookie = () => document.cookie.split(';')
                        .map((part) => part.trim())
                        .find((part) => part.startsWith(prefix))
                        ?.substring(prefix.length);
                      let rawValue = readCsrfCookie();
                      if (!rawValue) {
                        try {
                          const response = await fetch('/api/auth/csrf', {
                            credentials: 'include',
                            headers: { 'Accept': 'application/json' }
                          });
                          if (!response.ok) return request;
                          rawValue = readCsrfCookie();
                        } catch (_ignored) {
                          return request;
                        }
                      }
                      if (!rawValue) return request;
                      try {
                        request.headers = request.headers || {};
                        request.headers['%s'] = decodeURIComponent(rawValue);
                      } catch (_ignored) {
                        // Leave the header absent when a malformed cookie cannot be decoded.
                      }
                      return request;
                    },
                    presets: [
                    """.formatted(cookieName, headerName);
            return swaggerInitializer.replace("presets: [", interceptor);
        }
    }
}
