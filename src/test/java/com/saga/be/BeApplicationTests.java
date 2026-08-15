package com.saga.be;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
		"app.student-invitation.gmail-api.client-id=",
		"app.student-invitation.gmail-api.client-secret=",
		"app.student-invitation.gmail-api.refresh-token=",
		"app.student-invitation.gmail-api.sender-email=",
		"app.student-invitation.gmail-api.sender-name="
})
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class BeApplicationTests {

	@Autowired
	private StudentInvitationDeliveryAdapter studentInvitationDeliveryAdapter;

	@Autowired
	private HealthContributorRegistry healthContributorRegistry;

	@Test
	void contextLoads() {
		assertInstanceOf(
				UnavailableStudentInvitationDeliveryAdapter.class,
				studentInvitationDeliveryAdapter
		);
		assertNull(healthContributorRegistry.getContributor("mail"));
	}

}
