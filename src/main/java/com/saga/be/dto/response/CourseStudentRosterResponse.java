package com.saga.be.dto.response;

import org.springframework.data.domain.Page;

public record CourseStudentRosterResponse(
        Page<CourseStudentRosterItem> studentsWithTeam,
        Page<CourseStudentRosterItem> studentsWithoutTeam
) {
}
