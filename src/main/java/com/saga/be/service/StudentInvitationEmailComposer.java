package com.saga.be.service;

import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudentInvitationEmailComposer {

    private final StudentInvitationProperties properties;

    public StudentInvitationEmailComposer(StudentInvitationProperties properties) {
        this.properties = properties;
    }

    public StudentInvitationMessage compose(
            StudentCourseInvitation invitation,
            List<String> teamNames
    ) {
        String courseName = invitation.getCourse().getName();
        String teamSummary = teamNames.isEmpty()
                ? ""
                : " Team: " + String.join(", ", teamNames) + ".";
        String loginInstruction = invitation.getInvitationType()
                == StudentInvitationType.LINKED_STUDENT
                ? "Please sign in to SAGA to view your course and team."
                : "Please sign in or register with this same email address to link your "
                        + "existing SAGA course and team. You may use Google when the Cognito "
                        + "deployment supports it.";
        String body = "You have been added to Course " + courseName + "."
                + teamSummary
                + " " + loginInstruction
                + " Start sign-in here: " + properties.loginUri();
        return new StudentInvitationMessage(
                invitation.getStudent().getEmail(),
                "You have been added to Course " + courseName,
                body,
                invitation.getInvitationType(),
                courseName,
                List.copyOf(teamNames),
                properties.loginUri()
        );
    }
}
