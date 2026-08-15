package com.saga.be.dto.response;

import java.util.UUID;

public record AdminTeamReadResponse(
        UUID id,
        String name,
        CourseSummary course,
        ProjectSummary project
) {
    public record CourseSummary(UUID id, String courseCode, String name) {
    }

    public record ProjectSummary(UUID id, String name) {
    }
}
