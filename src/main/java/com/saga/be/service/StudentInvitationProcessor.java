package com.saga.be.service;

import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.repository.StudentCourseInvitationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        name = "app.student-invitation.processing-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class StudentInvitationProcessor {

    private final StudentCourseInvitationRepository invitationRepository;
    private final StudentInvitationClaimService claimService;
    private final StudentInvitationDeliveryAdapter deliveryAdapter;
    private final StudentInvitationProperties invitationProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationQueued(StudentInvitationQueued event) {
        process(event.invitationId());
    }

    @Scheduled(fixedDelayString = "${app.student-invitation.retry-delay-ms:60000}")
    public void retryFailedDeliveries() {
        List<UUID> invitationIds = new ArrayList<>(
                invitationRepository.findTop100IdsByInvitationStatusInOrderByCreatedAtAsc(
                List.of(StudentInvitationStatus.PENDING, StudentInvitationStatus.FAILED)
                )
        );
        LocalDateTime staleBefore = LocalDateTime.now()
                .minus(invitationProperties.processingTimeout());
        invitationRepository.findTop100IdsByProcessingStartedAtBefore(
                StudentInvitationStatus.PROCESSING,
                staleBefore
        ).forEach(invitationId -> {
            if (claimService.recoverStaleProcessing(invitationId, staleBefore)) {
                invitationIds.add(invitationId);
            }
        });
        invitationIds.forEach(this::process);
    }

    public void process(UUID invitationId) {
        claimService.claim(invitationId).ifPresent(message -> {
            try {
                deliveryAdapter.deliver(message);
                claimService.markSent(invitationId);
            } catch (StudentInvitationDeliveryUnavailableException exception) {
                claimService.markFailed(invitationId, "DELIVERY_UNAVAILABLE");
                log.warn("Student invitation delivery is not configured; invitationId={}", invitationId);
            } catch (RuntimeException exception) {
                claimService.markFailed(invitationId, "DELIVERY_FAILED");
                log.warn("Student invitation delivery failed; invitationId={}", invitationId);
            }
        });
    }
}
