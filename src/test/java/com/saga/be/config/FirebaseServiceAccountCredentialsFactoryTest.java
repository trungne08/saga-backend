package com.saga.be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class FirebaseServiceAccountCredentialsFactoryTest {

    private final FirebaseServiceAccountCredentialsFactory factory =
            new FirebaseServiceAccountCredentialsFactory(JsonMapper.builder().build());

    @Test
    void missingEnvironmentLeavesProviderUnavailableWithoutCreatingAFile() {
        assertThat(factory.fromEnvironment(new MockEnvironment())).isEmpty();
    }

    @Test
    void partialEnvironmentFailsClosedWithoutEchoingCredentialValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("FIREBASE_PROJECT_ID", "placeholder-project");

        assertThatThrownBy(() -> factory.fromEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase service-account environment is incomplete")
                .hasMessageNotContaining("placeholder-project");
    }

    @Test
    void literalBackslashNIsNormalizedButRuntimeMultilineIsPreserved() {
        assertThat(FirebaseServiceAccountCredentialsFactory.normalizePrivateKey("line1\\nline2"))
                .isEqualTo("line1\nline2");
        assertThat(FirebaseServiceAccountCredentialsFactory.normalizePrivateKey("line1\nline2"))
                .isEqualTo("line1\nline2");
    }
}
