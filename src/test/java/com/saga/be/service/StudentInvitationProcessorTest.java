package com.saga.be.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.repository.StudentCourseInvitationRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentInvitationProcessorTest {

    @Mock
    private StudentCourseInvitationRepository invitationRepository;

    @Mock
    private StudentInvitationClaimService claimService;

    @Mock
    private StudentInvitationDeliveryAdapter deliveryAdapter;

    @Test
    void marksInvitationSentOnlyAfterAdapterAcceptsDelivery() {
        UUID invitationId = UUID.randomUUID();
        when(claimService.claim(invitationId)).thenReturn(Optional.of(message()));

        processor().process(invitationId);

        verify(deliveryAdapter).deliver(message());
        verify(claimService).markSent(invitationId);
        verify(claimService, never()).markFailed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void marksFailureWithoutThrowingWhenProviderIsUnavailable() {
        UUID invitationId = UUID.randomUUID();
        StudentInvitationMessage message = message();
        when(claimService.claim(invitationId)).thenReturn(Optional.of(message));
        org.mockito.Mockito.doThrow(new StudentInvitationDeliveryUnavailableException())
                .when(deliveryAdapter).deliver(message);

        processor().process(invitationId);

        verify(claimService).markFailed(invitationId, "DELIVERY_UNAVAILABLE");
        verify(claimService, never()).markSent(invitationId);
    }

    @Test
    void marksFailureAndNeverSentWhenGmailApiProviderFails() {
        UUID invitationId = UUID.randomUUID();
        StudentInvitationMessage message = message();
        when(claimService.claim(invitationId)).thenReturn(Optional.of(message));
        org.mockito.Mockito.doThrow(new StudentInvitationDeliveryException(
                "GMAIL_PROVIDER_UNAVAILABLE",
                true,
                503,
                new IllegalStateException("provider failure")
        )).when(deliveryAdapter).deliver(message);

        processor().process(invitationId);

        verify(claimService).markFailed(invitationId, "DELIVERY_FAILED");
        verify(claimService, never()).markSent(invitationId);
    }

    @Test
    void retriesOnlyPendingAndFailedOutboxRecords() {
        UUID pending = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        when(invitationRepository.findTop100IdsByInvitationStatusInOrderByCreatedAtAsc(
                List.of(StudentInvitationStatus.PENDING, StudentInvitationStatus.FAILED)
        )).thenReturn(List.of(pending, failed));
        when(invitationRepository.findTop100IdsByProcessingStartedAtBefore(
                org.mockito.ArgumentMatchers.eq(StudentInvitationStatus.PROCESSING),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());
        when(claimService.claim(pending)).thenReturn(Optional.empty());
        when(claimService.claim(failed)).thenReturn(Optional.empty());

        processor().retryFailedDeliveries();

        verify(claimService).claim(pending);
        verify(claimService).claim(failed);
    }

    private StudentInvitationProcessor processor() {
        StudentInvitationProperties properties = new StudentInvitationProperties();
        properties.setProcessingTimeoutMs(1000);
        return new StudentInvitationProcessor(
                invitationRepository,
                claimService,
                deliveryAdapter,
                properties
        );
    }

    private StudentInvitationMessage message() {
        return new StudentInvitationMessage(
                "student@example.test",
                "Course invitation",
                "Body",
                com.saga.be.entity.enums.StudentInvitationType.FIRST_LOGIN_REQUIRED,
                "Course",
                List.of(),
                URI.create("https://frontend.example.test/login")
        );
    }
}
