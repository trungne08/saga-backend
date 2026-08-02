package com.saga.be.dto.response;

import java.util.UUID;

public record CourseStudentRosterItem(
        UUID studentId,
        String fullName,
        String studentCode,
        String email,
        CourseStudentTeamSummaryResponse team
) {
}
