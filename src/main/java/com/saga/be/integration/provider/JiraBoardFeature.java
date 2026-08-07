package com.saga.be.integration.provider;

/** Safe, machine-readable subset of a Jira Agile board feature. */
public record JiraBoardFeature(
        String boardFeature,
        String featureId,
        String state,
        String boardId
) {
    public String identifier() {
        return boardFeature != null && !boardFeature.isBlank() ? boardFeature : featureId;
    }
}
