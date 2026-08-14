package com.saga.be.dto.response;

import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;

public record CourseStudentBasicInfoResponse(
        UUID courseId,
        UUID studentId,
        String studentCode,
        String fullName,
        String email,
        String avatarUrl,
        AccountStatus accountStatus,
        TeamInfo team
) {

    public static CourseStudentBasicInfoResponse from(
            UUID courseId,
            TeamMember membership
    ) {
        Student student = membership.getStudent();
        Team team = membership.getTeam();
        return new CourseStudentBasicInfoResponse(
                courseId,
                student.getId(),
                student.getStudentCode(),
                student.getFullName(),
                student.getEmail(),
                student.getAvatarUrl(),
                student.getAccountStatus(),
                new TeamInfo(
                        team.getId(),
                        team.getName(),
                        membership.getRoleInTeam()
                )
        );
    }

    public record TeamInfo(
            UUID teamId,
            String teamName,
            RoleInTeam roleInTeam
    ) {
    }
}
