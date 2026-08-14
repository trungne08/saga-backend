package com.saga.be.controller;

import com.saga.be.dto.request.AgentConversationCreateRequest;
import com.saga.be.dto.request.AgentMessageSendRequest;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AgentGatewayService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
public class AgentGatewayController {

    private final AgentGatewayService gateway;

    public AgentGatewayController(AgentGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/conversations")
    public ResponseEntity<AgentApiResponses.Conversation> create(
            @AuthenticationPrincipal SagaPrincipal actor,
            @Valid @RequestBody AgentConversationCreateRequest request
    ) {
        return ResponseEntity.status(201).body(gateway.create(actor, request));
    }

    @GetMapping("/conversations")
    public AgentApiResponses.ConversationList list(
            @AuthenticationPrincipal SagaPrincipal actor
    ) {
        return gateway.list(actor);
    }

    @GetMapping("/conversations/{conversationId}")
    public AgentApiResponses.Conversation get(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID conversationId
    ) {
        return gateway.get(actor, conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AgentApiResponses.Chat send(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AgentMessageSendRequest request
    ) {
        return gateway.send(actor, conversationId, request);
    }

    @PostMapping("/pending-actions/{actionId}/confirm")
    public AgentApiResponses.ActionExecution confirm(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID actionId
    ) {
        return gateway.confirm(actor, actionId);
    }

    @PostMapping("/pending-actions/{actionId}/reject")
    public AgentApiResponses.PendingAction reject(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID actionId
    ) {
        return gateway.reject(actor, actionId);
    }

    @GetMapping("/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID artifactId
    ) {
        AgentGatewayService.DownloadedArtifact artifact = gateway.download(actor, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(artifact.filename(), StandardCharsets.UTF_8)
                                .build().toString()
                )
                .body(artifact.content());
    }
}
