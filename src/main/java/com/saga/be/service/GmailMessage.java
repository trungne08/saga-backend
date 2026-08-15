package com.saga.be.service;

public record GmailMessage(
        String recipientEmail,
        String subject,
        String textBody,
        String htmlBody
) {
}
