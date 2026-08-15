package com.saga.be.config;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Component
public class FirebaseServiceAccountCredentialsFactory {

    private static final List<String> REQUIRED_ENVIRONMENT_VARIABLES = List.of(
            "FIREBASE_TYPE",
            "FIREBASE_PROJECT_ID",
            "FIREBASE_PRIVATE_KEY_ID",
            "FIREBASE_PRIVATE_KEY",
            "FIREBASE_CLIENT_EMAIL",
            "FIREBASE_CLIENT_ID",
            "FIREBASE_AUTH_URI",
            "FIREBASE_TOKEN_URI",
            "FIREBASE_AUTH_PROVIDER_X509_CERT_URL",
            "FIREBASE_CLIENT_X509_CERT_URL",
            "FIREBASE_UNIVERSE_DOMAIN"
    );

    private final ObjectMapper objectMapper;

    public FirebaseServiceAccountCredentialsFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<GoogleCredentials> fromEnvironment(Environment environment) {
        boolean anyConfigured = REQUIRED_ENVIRONMENT_VARIABLES.stream()
                .anyMatch(name -> StringUtils.hasText(environment.getProperty(name)));
        if (!anyConfigured) {
            return Optional.empty();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String name : REQUIRED_ENVIRONMENT_VARIABLES) {
            String value = environment.getProperty(name);
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException("Firebase service-account environment is incomplete");
            }
            values.put(name, value.trim());
        }
        if (!"service_account".equals(values.get("FIREBASE_TYPE"))) {
            throw new IllegalStateException("Firebase credential type is invalid");
        }

        Map<String, String> serviceAccount = new LinkedHashMap<>();
        serviceAccount.put("type", values.get("FIREBASE_TYPE"));
        serviceAccount.put("project_id", values.get("FIREBASE_PROJECT_ID"));
        serviceAccount.put("private_key_id", values.get("FIREBASE_PRIVATE_KEY_ID"));
        serviceAccount.put("private_key", normalizePrivateKey(values.get("FIREBASE_PRIVATE_KEY")));
        serviceAccount.put("client_email", values.get("FIREBASE_CLIENT_EMAIL"));
        serviceAccount.put("client_id", values.get("FIREBASE_CLIENT_ID"));
        serviceAccount.put("auth_uri", values.get("FIREBASE_AUTH_URI"));
        serviceAccount.put("token_uri", values.get("FIREBASE_TOKEN_URI"));
        serviceAccount.put(
                "auth_provider_x509_cert_url",
                values.get("FIREBASE_AUTH_PROVIDER_X509_CERT_URL")
        );
        serviceAccount.put(
                "client_x509_cert_url",
                values.get("FIREBASE_CLIENT_X509_CERT_URL")
        );
        serviceAccount.put("universe_domain", values.get("FIREBASE_UNIVERSE_DOMAIN"));

        byte[] json = null;
        try {
            json = objectMapper.writeValueAsBytes(serviceAccount);
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(json)
            ).createScoped("https://www.googleapis.com/auth/firebase.messaging");
            return Optional.of(credentials);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Firebase service-account environment is invalid");
        } finally {
            if (json != null) {
                Arrays.fill(json, (byte) 0);
            }
        }
    }

    static String normalizePrivateKey(String value) {
        return value.contains("\\n")
                ? value.replace("\\n", "\n")
                : value;
    }
}
