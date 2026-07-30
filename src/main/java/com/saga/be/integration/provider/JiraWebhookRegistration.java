package com.saga.be.integration.provider;

/** Result of making a Jira dynamic-webhook registration idempotent. */
public record JiraWebhookRegistration(String webhookId, boolean created) {
}
