package com.saga.be.integration.provider;

public record JiraAttachmentSnapshot(
        String id,
        String filename,
        String mimeType,
        Long size,
        String authorAccountId
) {
}
