package com.saga.be.dto.response;

import java.util.UUID;

/** Native aggregate projection for the paged administrator course overview. */
public interface AdminCourseProgressOverviewRow {

    UUID getCourseId();

    String getCourseCode();

    String getCourseName();

    UUID getLecturerId();

    String getLecturerFullName();

    long getTeamCount();

    long getStudentCount();

    long getProjectCount();

    long getSprintCount();

    long getActiveSprintCount();

    long getClosedSprintCount();

    long getPeerReviewCount();
}
