package com.saga.be.integration.provider;

import java.util.Arrays;

public record JiraAttachmentUpload(
        String filename,
        String contentType,
        byte[] content
) {
    public JiraAttachmentUpload {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
