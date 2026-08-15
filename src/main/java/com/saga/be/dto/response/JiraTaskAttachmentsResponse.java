package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record JiraTaskAttachmentsResponse(
        UUID taskId,
        List<Item> attachments,
        List<LinkItem> links
) {
    public JiraTaskAttachmentsResponse {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        links = links == null ? List.of() : List.copyOf(links);
    }

    public record Item(
            UUID id,
            String externalId,
            String filename,
            String mimeType,
            Long sizeBytes
    ) {
    }

    public record LinkItem(
            UUID id,
            String url,
            String remoteLinkId
    ) {
    }
}
