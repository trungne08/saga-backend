package com.saga.be.dto.response;

import java.util.UUID;

/** Local operational counts only; this is not a grade or completion decision. */
public record AdminCourseProgressOverviewResponse(
        UUID courseId,
        String courseCode,
        String courseName,
        LecturerSummary lecturer,
        long teamCount,
        long studentCount,
        long projectCount,
        long sprintCount,
        long activeSprintCount,
        long closedSprintCount,
        long peerReviewCount
) {

    public record LecturerSummary(UUID lecturerId, String fullName) {
    }
}
