package com.saga.be.controller;

import com.saga.be.dto.response.WebhookAcceptedResponse;
import com.saga.be.integration.webhook.WebhookIngestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Webhook", description = "Endpoint dành riêng cho webhook của nhà cung cấp.")
public class WebhookController {

    private final WebhookIngestionService ingestionService;

    public WebhookController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/api/webhooks/github")
    public ResponseEntity<WebhookAcceptedResponse> github(
            @RequestBody byte[] payload,
            @RequestHeader(
                    value = "X-Hub-Signature-256",
                    required = false
            ) String signature,
            @RequestHeader(
                    value = "X-GitHub-Delivery",
                    required = false
            ) String delivery,
            @RequestHeader(
                    value = "X-GitHub-Event",
                    required = false
            ) String event,
            HttpServletRequest request
    ) {
        WebhookAcceptedResponse response = ingestionService.receiveGitHub(
                payload,
                signature,
                delivery,
                event,
                request.getRemoteAddr()
        );
        if ("PING".equals(response.status())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/api/webhooks/jira")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebhookAcceptedResponse jira(
            @RequestBody byte[] payload,
            @RequestParam(required = false) String token,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization,
            @RequestHeader(
                    value = "X-Atlassian-Webhook-Identifier",
                    required = false
            ) String webhookIdentifier,
            HttpServletRequest request
    ) {
        return ingestionService.receiveJira(
                payload,
                authorization,
                token,
                webhookIdentifier,
                request.getRemoteAddr()
        );
    }
}
