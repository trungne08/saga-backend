package com.saga.be.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class GmailSmtpStudentInvitationDeliveryAdapter
        implements StudentInvitationDeliveryAdapter {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public GmailSmtpStudentInvitationDeliveryAdapter(
            JavaMailSender mailSender,
            String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void deliver(StudentInvitationMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(message.recipientEmail());
            helper.setSubject(message.subject());
            helper.setText(message.body(), message.htmlBody());
            mailSender.send(mimeMessage);
        } catch (MailException exception) {
            throw new StudentInvitationDeliveryException(
                    category(exception),
                    exception
            );
        } catch (MessagingException exception) {
            throw new StudentInvitationDeliveryException(
                    "MESSAGE_BUILD",
                    exception
            );
        }
    }

    private String category(MailException exception) {
        if (exception instanceof MailAuthenticationException) {
            return "AUTHENTICATION";
        }
        if (exception instanceof MailSendException) {
            return "SMTP_SEND";
        }
        return "MAIL_PROVIDER";
    }
}
