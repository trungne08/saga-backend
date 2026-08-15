package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record SprintPeerReviewResponse(
        UUID teamId,
        UUID sprintId,
        String sprintName,
        List<PeerReviewResponse> reviews
) {}
