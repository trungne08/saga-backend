package com.saga.be.dto.response;

import com.saga.be.service.ExcelImportService.CourseStudentImportSummary;

public record CourseStudentImportResponse(
        String operation,
        String message,
        int totalRows,
        int createdStudents,
        int reusedStudents,
        int invitationsQueued,
        int teamsCreated,
        int membershipsCreated,
        boolean groupingApplied
) {
    public static CourseStudentImportResponse from(
            String operation,
            String message,
            CourseStudentImportSummary summary
    ) {
        return new CourseStudentImportResponse(
                operation,
                message,
                summary.totalRows(),
                summary.createdStudents(),
                summary.reusedStudents(),
                summary.invitationsQueued(),
                summary.teamsCreated(),
                summary.membershipsCreated(),
                summary.groupingApplied()
        );
    }
}
