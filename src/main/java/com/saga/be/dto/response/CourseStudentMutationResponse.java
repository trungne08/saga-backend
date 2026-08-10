package com.saga.be.dto.response;

import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;

public record CourseStudentMutationResponse(
        String operation,
        String message,
        UUID studentId,
        String studentCode,
        String email,
        String fullName,
        boolean enrolledInCourse,
        UUID teamId,
        String teamName,
        RoleInTeam roleInTeam
) {
}
