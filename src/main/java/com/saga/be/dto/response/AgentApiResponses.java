package com.saga.be.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentApiResponses {

    private AgentApiResponses() {
    }

    public record Conversation(
            UUID id,
            String title,
            String applicationRoleSnapshot,
            boolean archived,
            String createdAt,
            String updatedAt,
            List<Message> messages
    ) {
    }

    public record ConversationList(List<Conversation> items) {
    }

    public record Message(
            UUID id,
            String role,
            String content,
            String provider,
            String model,
            String createdAt
    ) {
    }

    public record Chat(
            UUID conversationId,
            UUID messageId,
            String text,
            String status,
            List<Map<String, Object>> citations,
            PendingAction pendingAction,
            GeneratedArtifact generatedArtifact,
            JobReference jobReference,
            List<String> suggestedFollowups,
            String provider,
            String model
    ) {
    }

    public record PendingAction(
            UUID id,
            String conversationId,
            String actionType,
            String status,
            String summary,
            String idempotencyKey,
            String expiresAt,
            String safeErrorCode,
            Map<String, Object> payload
    ) {
    }

    public record GeneratedArtifact(
            UUID id,
            String conversationId,
            String artifactType,
            String scopeType,
            String scopeId,
            String filename,
            String mediaType
    ) {
    }

    public record JobReference(
            UUID jobId,
            String jobType,
            String status,
            String currentStep,
            int runNumber,
            String projectId,
            String safeErrorCode,
            Map<String, Object> finalResult
    ) {
    }

    public record ActionExecution(
            UUID actionId,
            String status,
            TaskReadResponse task
    ) {
    }
}
