package com.saga.be.dto.response;

import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.UUID;

public record TeamMemberResponse(
        UUID studentId,
        String fullName,
        String studentCode,
        RoleInTeam roleInTeam
) {

    public static TeamMemberResponse from(TeamMember membership) {
        return new TeamMemberResponse(
                membership.getStudent().getId(),
                membership.getStudent().getFullName(),
                membership.getStudent().getStudentCode(),
                membership.getRoleInTeam()
        );
    }
}
