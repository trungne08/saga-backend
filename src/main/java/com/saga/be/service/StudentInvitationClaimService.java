package com.saga.be.service;

import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.TeamMemberRepository;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentInvitationClaimService {

    private static final int MAX_ATTEMPTS = 5;

    private final StudentCourseInvitationRepository invitationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final StudentInvitationEmailComposer composer;

    public StudentInvitationClaimService(
            StudentCourseInvitationRepository invitationRepository,
            TeamMemberRepository teamMemberRepository,
            StudentInvitationEmailComposer composer
    ) {
        this.invitationRepository = invitationRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.composer = composer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StudentInvitationMessage> claim(UUID invitationId) {
        StudentCourseInvitation invitation = invitationRepository.findForUpdateById(invitationId)
                .orElse(null);
        if (invitation == null
                || invitation.getInvitationStatus() == StudentInvitationStatus.SENT
                || invitation.getInvitationStatus() == StudentInvitationStatus.PROCESSING
                || invitation.getAttemptCount() >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        LocalDateTime claimedAt = LocalDateTime.now();
        invitation.setInvitationStatus(StudentInvitationStatus.PROCESSING);
        invitation.setAttemptCount(invitation.getAttemptCount() + 1);
        invitation.setLastAttemptAt(claimedAt);
        invitation.setProcessingStartedAt(claimedAt);
        invitation.setFailureCode(null);
        invitationRepository.saveAndFlush(invitation);
        List<String> teamNames = teamMemberRepository.findByStudentId(invitation.getStudent().getId())
                .stream()
                .filter(member -> invitation.getCourse().getId()
                        .equals(member.getTeam().getCourse().getId()))
                .map(member -> member.getTeam().getName())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        return Optional.of(composer.compose(invitation, teamNames));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStaleProcessing(UUID invitationId, LocalDateTime staleBefore) {
        StudentCourseInvitation invitation = invitationRepository.findForUpdateById(invitationId)
                .orElse(null);
        if (invitation == null
                || invitation.getInvitationStatus() != StudentInvitationStatus.PROCESSING
                || invitation.getProcessingStartedAt() == null
                || !invitation.getProcessingStartedAt().isBefore(staleBefore)) {
            return false;
        }

        invitation.setProcessingStartedAt(null);
        if (invitation.getAttemptCount() >= MAX_ATTEMPTS) {
            invitation.setInvitationStatus(StudentInvitationStatus.FAILED);
            invitation.setFailureCode("MAX_ATTEMPTS_EXHAUSTED");
            invitationRepository.saveAndFlush(invitation);
            return false;
        }

        invitation.setInvitationStatus(StudentInvitationStatus.FAILED);
        invitation.setFailureCode("PROCESSING_TIMEOUT");
        invitationRepository.saveAndFlush(invitation);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID invitationId) {
        invitationRepository.findForUpdateById(invitationId).ifPresent(invitation -> {
            if (invitation.getInvitationStatus() == StudentInvitationStatus.PROCESSING) {
                invitation.setInvitationStatus(StudentInvitationStatus.SENT);
                invitation.setSentAt(LocalDateTime.now());
                invitation.setProcessingStartedAt(null);
                invitation.setFailureCode(null);
                invitationRepository.saveAndFlush(invitation);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID invitationId, String failureCode) {
        invitationRepository.findForUpdateById(invitationId).ifPresent(invitation -> {
            if (invitation.getInvitationStatus() == StudentInvitationStatus.PROCESSING) {
                invitation.setInvitationStatus(StudentInvitationStatus.FAILED);
                invitation.setProcessingStartedAt(null);
                invitation.setFailureCode(failureCode);
                invitationRepository.saveAndFlush(invitation);
            }
        });
    }
}
