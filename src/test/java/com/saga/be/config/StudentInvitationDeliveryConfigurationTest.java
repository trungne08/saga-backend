package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.saga.be.service.GmailApiStudentInvitationDeliveryAdapter;
import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StudentInvitationDeliveryConfigurationTest {

    @Test
    void everyGmailApiSettingIsRequiredButMissingConfigurationDoesNotFailStartup() {
        List<GmailApiStudentInvitationProperties> incomplete = List.of(
                properties("", "secret", "refresh", "sender@example.test", "SAGA"),
                properties("client", "", "refresh", "sender@example.test", "SAGA"),
                properties("client", "secret", "", "sender@example.test", "SAGA"),
                properties("client", "secret", "refresh", "", "SAGA"),
                properties("client", "secret", "refresh", "sender@example.test", "")
        );

        incomplete.forEach(properties -> assertInstanceOf(
                UnavailableStudentInvitationDeliveryAdapter.class,
                adapter(properties)
        ));
    }

    @Test
    void completeGmailApiConfigurationUsesHttpsAdapterWithoutProviderCall() {
        StudentInvitationDeliveryAdapter adapter = adapter(properties(
                "client",
                "secret",
                "refresh",
                "sender@example.test",
                "SAGA"
        ));

        assertInstanceOf(GmailApiStudentInvitationDeliveryAdapter.class, adapter);
    }

    private StudentInvitationDeliveryAdapter adapter(
            GmailApiStudentInvitationProperties properties
    ) {
        return new StudentInvitationDeliveryConfiguration()
                .studentInvitationDeliveryAdapter(
                        properties,
                        new IntegrationProperties(
                                "", "", "", Duration.ofMinutes(10),
                                Duration.ofSeconds(1), Duration.ofSeconds(2),
                                false, Duration.ofMinutes(5)
                        ),
                        JsonMapper.builder().build()
                );
    }

    private GmailApiStudentInvitationProperties properties(
            String clientId,
            String clientSecret,
            String refreshToken,
            String senderEmail,
            String senderName
    ) {
        return new GmailApiStudentInvitationProperties(
                clientId,
                clientSecret,
                refreshToken,
                senderEmail,
                senderName
        );
    }
}
