package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record CourseStudentTeamSummaryResponse(
        UUID teamId,
        String teamName,
        UUID projectId,
        String projectName,
        List<TeamMemberResponse> teamMembers
) {
}
