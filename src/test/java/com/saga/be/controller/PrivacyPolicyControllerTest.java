package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PrivacyPolicyControllerTest {

    @Test
    void rejectsMissingOrInvalidPublicContactConfigurationAtRenderTime() {
        for (String invalidUrl : new String[]{"", "mailto:privacy@example.test", "https://user@example.test"}) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> new PrivacyPolicyController(invalidUrl).getPrivacyPolicy()
            );
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        }
    }
}
