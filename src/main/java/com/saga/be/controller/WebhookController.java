package com.saga.be.controller;

import com.saga.be.dto.response.WebhookAcceptedResponse;
import com.saga.be.integration.webhook.WebhookIngestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final WebhookIngestionService ingestionService;

    public WebhookController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/api/webhooks/github")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebhookAcceptedResponse github(
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
        return ingestionService.receiveGitHub(
                payload,
                signature,
                delivery,
                event,
                request.getRemoteAddr()
        );
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
