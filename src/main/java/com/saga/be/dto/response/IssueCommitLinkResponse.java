package com.saga.be.dto.response;

import java.util.UUID;

public record IssueCommitLinkResponse(UUID issueId, UUID commitId, boolean linked) {
}
