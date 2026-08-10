package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.service.GmailSmtpStudentInvitationDeliveryAdapter;
import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

class StudentInvitationDeliveryConfigurationTest {

    @Test
    void missingMailConfigurationUsesUnavailableAdapterWithoutFailingStartup() {
        ObjectProvider<JavaMailSender> provider = provider(null);

        StudentInvitationDeliveryAdapter adapter = configuration().studentInvitationDeliveryAdapter(
                provider,
                new MockEnvironment()
        );

        assertInstanceOf(UnavailableStudentInvitationDeliveryAdapter.class, adapter);
    }

    @Test
    void completeGmailConfigurationUsesSmtpAdapter() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.mail.host", "smtp.gmail.com")
                .withProperty("spring.mail.port", "587")
                .withProperty("spring.mail.username", "sender@example.test")
                .withProperty("spring.mail.password", "test-only-value")
                .withProperty("spring.mail.properties.mail.smtp.auth", "true")
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true");

        StudentInvitationDeliveryAdapter adapter = configuration().studentInvitationDeliveryAdapter(
                provider(mailSender),
                environment
        );

        assertInstanceOf(GmailSmtpStudentInvitationDeliveryAdapter.class, adapter);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return provider;
    }

    private StudentInvitationDeliveryConfiguration configuration() {
        return new StudentInvitationDeliveryConfiguration();
    }
}
