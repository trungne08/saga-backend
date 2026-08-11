package com.saga.be.dto.response;

import java.util.UUID;

public record TaskIssueLinkResponse(UUID taskId, UUID issueId, boolean linked) {
}
