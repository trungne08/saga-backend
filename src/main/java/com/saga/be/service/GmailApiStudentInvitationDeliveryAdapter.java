package com.saga.be.service;

import java.time.Clock;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

public class GmailApiStudentInvitationDeliveryAdapter
        implements StudentInvitationDeliveryAdapter {

    static final String TOKEN_URI = GmailApiTransport.TOKEN_URI;
    static final String SEND_URI = GmailApiTransport.SEND_URI;

    private final GmailApiTransport transport;

    public GmailApiStudentInvitationDeliveryAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            String clientId,
            String clientSecret,
            String refreshToken,
            String senderEmail,
            String senderName
    ) {
        this(restClient, objectMapper, Clock.systemUTC(), clientId, clientSecret,
                refreshToken, senderEmail, senderName);
    }

    GmailApiStudentInvitationDeliveryAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock,
            String clientId,
            String clientSecret,
            String refreshToken,
            String senderEmail,
            String senderName
    ) {
        this.transport = new GmailApiTransport(
                restClient,
                objectMapper,
                clock,
                clientId,
                clientSecret,
                refreshToken,
                senderEmail,
                senderName
        );
    }

    @Override
    public void deliver(StudentInvitationMessage message) {
        try {
            transport.send(new GmailMessage(
                    message.recipientEmail(),
                    message.subject(),
                    message.body(),
                    message.htmlBody()
            ));
        } catch (GmailDeliveryException exception) {
            throw new StudentInvitationDeliveryException(
                    exception.getCategory(),
                    exception.isRetryable(),
                    exception.getHttpStatus(),
                    exception
            );
        }
    }
}
