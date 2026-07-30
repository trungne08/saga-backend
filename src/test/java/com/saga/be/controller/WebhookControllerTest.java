package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.WebhookAcceptedResponse;
import com.saga.be.integration.webhook.WebhookIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class WebhookControllerTest {

    @Test
    void githubPingMapsVerifiedPingResultToHttpOk() {
        WebhookIngestionService ingestion = mock(WebhookIngestionService.class);
        byte[] payload = new byte[]{1};
        when(ingestion.receiveGitHub(
                payload, "sha256=valid", "delivery", "ping", "127.0.0.1"))
                .thenReturn(new WebhookAcceptedResponse("PING"));
        WebhookController controller = new WebhookController(ingestion);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var response = controller.github(
                payload, "sha256=valid", "delivery", "ping", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PING", response.getBody().status());
    }
}
