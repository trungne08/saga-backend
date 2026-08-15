package com.saga.be.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FirebaseAdminInitializer {

    private static final String APP_NAME = "saga-notifications";

    private final FirebaseServiceAccountCredentialsFactory credentialsFactory;

    public FirebaseAdminInitializer(
            FirebaseServiceAccountCredentialsFactory credentialsFactory
    ) {
        this.credentialsFactory = credentialsFactory;
    }

    public Optional<FirebaseMessaging> initialize(Environment environment) {
        try {
            Optional<GoogleCredentials> explicit = credentialsFactory.fromEnvironment(environment);
            GoogleCredentials credentials;
            if (explicit.isPresent()) {
                credentials = explicit.get();
            } else if (StringUtils.hasText(
                    environment.getProperty("GOOGLE_APPLICATION_CREDENTIALS")
            )) {
                credentials = GoogleCredentials.getApplicationDefault();
            } else {
                return Optional.empty();
            }

            FirebaseApp app = existingApp().orElseGet(() -> initializeApp(
                    environment,
                    credentials
            ));
            return Optional.of(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<FirebaseApp> existingApp() {
        return FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst();
    }

    private FirebaseApp initializeApp(
            Environment environment,
            GoogleCredentials credentials
    ) {
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setConnectTimeout(5000)
                .setReadTimeout(10000)
                .setWriteTimeout(10000);
        String projectId = environment.getProperty("FIREBASE_PROJECT_ID");
        if (StringUtils.hasText(projectId)) {
            options.setProjectId(projectId.trim());
        }
        return FirebaseApp.initializeApp(options.build(), APP_NAME);
    }
}
