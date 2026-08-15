package com.saga.be.integration.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.config.IntegrationCallbackProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IntegrationCallbackRedirectServiceTest {

    @Test
    void redirectsWithOnlyTheOpaqueResultIdQueryParameter() {
        IntegrationCallbackRedirectService service =
                new IntegrationCallbackRedirectService(
                        new IntegrationCallbackProperties(
                                "https://frontend.example/integrations/callback",
                                Duration.ofMinutes(5)
                        )
                );

        assertEquals(
                "https://frontend.example/integrations/callback?resultId=opaque-id",
                service.callbackResultUri("opaque-id").toString()
        );
    }
}
