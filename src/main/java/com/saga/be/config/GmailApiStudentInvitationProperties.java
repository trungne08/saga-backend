package com.saga.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.student-invitation.gmail-api")
public record GmailApiStudentInvitationProperties(
        String clientId,
        String clientSecret,
        String refreshToken,
        String senderEmail,
        String senderName
) {
}
