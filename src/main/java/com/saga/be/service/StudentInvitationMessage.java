package com.saga.be.service;

import com.saga.be.entity.enums.StudentInvitationType;
import java.net.URI;
import java.util.List;

public record StudentInvitationMessage(
        String recipientEmail,
        String subject,
        String body,
        StudentInvitationType invitationType,
        String courseName,
        List<String> teamNames,
        URI loginUri
) {
}
