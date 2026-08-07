package com.saga.be.integration.provider;

/**
 * Safe, machine-readable subset of a Jira Agile board feature.
 *
 * <p>The Jira Software API labels the Sprints board feature with the stable
 * {@code boardFeature} identifier {@value #SPRINTS_IDENTIFIER}; localized
 * names and descriptions are deliberately not retained.</p>
 */
public record JiraBoardFeature(
        String boardFeature,
        String featureId,
        String state,
        String boardId
) {
    public static final String SPRINTS_IDENTIFIER = "SPRINTS";
    public static final String ENABLED_STATE = "ENABLED";
    public static final String DISABLED_STATE = "DISABLED";

    public String identifier() {
        return boardFeature != null && !boardFeature.isBlank() ? boardFeature : featureId;
    }

    public boolean isSprintsFeature() {
        return SPRINTS_IDENTIFIER.equals(boardFeature);
    }
}
