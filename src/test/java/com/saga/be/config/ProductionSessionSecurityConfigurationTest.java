package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class ProductionSessionSecurityConfigurationTest {

    @Test
    void productionCookieDefaultsAndCsrfCookieConfigurationStayAligned() throws Exception {
        Properties production = productionProperties();
        assertEquals("${SESSION_COOKIE_SECURE:true}",
                production.getProperty("server.servlet.session.cookie.secure"));
        assertEquals("${SESSION_COOKIE_SAME_SITE:none}",
                production.getProperty("server.servlet.session.cookie.same-site"));

        CookieCsrfTokenRepository repository = (CookieCsrfTokenRepository) new SecurityConfig()
                .csrfTokenRepository(true, "none");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);

        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertEquals("/", csrfCookie.getPath());
        assertTrue(csrfCookie.getSecure());
        assertFalse(csrfCookie.isHttpOnly());
        assertEquals("none", csrfCookie.getAttribute("SameSite"));
    }

    @Test
    void corsRequiresExplicitOriginsAndAllowsCsrfAndIdempotencyHeaders() {
        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(
                "https://frontend.example.test, https://admin.example.test"
        );
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertEquals(List.of("https://frontend.example.test", "https://admin.example.test"),
                configuration.getAllowedOrigins());
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
        assertTrue(configuration.getAllowedHeaders().contains("X-XSRF-TOKEN"));
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
        assertThrows(IllegalStateException.class,
                () -> new CorsConfig().corsConfigurationSource("https://frontend.example.test,*"));
    }

    private Properties productionProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application-prod.properties")) {
            if (input == null) {
                throw new IllegalStateException("application-prod.properties is missing");
            }
            properties.load(input);
        }
        return properties;
    }
}
