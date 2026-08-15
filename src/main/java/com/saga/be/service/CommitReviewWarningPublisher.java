package com.saga.be.service;

import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.BusinessWarningCategory;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.TeamRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommitReviewWarningPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommitReviewWarningPublisher.class);

    private final BusinessWarningService warnings;
    private final TeamRepository teams;

    public CommitReviewWarningPublisher(BusinessWarningService warnings, TeamRepository teams) {
        this.warnings = warnings;
        this.teams = teams;
    }

    public void publish(CommitReviewIntent intent, String jobStatus, CommitReviewResultParser.ParsedResult parsed) {
        if (intent == null || parsed == null) {
            return;
        }
        try {
            if (CommitReviewResultParser.eligibleForNeedsChangesWarning(jobStatus, parsed)) {
                emitNeedsChanges(intent, parsed);
            } else if (CommitReviewResultParser.eligibleForUnlinkedAdvisory(jobStatus, parsed)) {
                emitUnlinkedAdvisory(intent, parsed);
            }
        } catch (RuntimeException exception) {
            log.warn("commit-review warning publish failed type={}", exception.getClass().getSimpleName());
        }
    }

    public void publishHistoricalDigest(GitRepo repo, HistoricalDigest digest) {
        if (repo == null || repo.getProject() == null || digest == null || digest.reviewedCount() <= 0) {
            return;
        }
        UUID projectId = repo.getProject().getId();
        Team team = teams.findByProjectId(projectId).orElse(null);
        String day = digest.day();
        String eventKey = "historical-digest:" + projectId + ":" + repo.getId() + ":" + day;
        String evidence = "Historical review digest: reviewed=" + digest.reviewedCount()
                + ", code-risk=" + digest.codeRiskCount()
                + ", poor-message=" + digest.poorMessageCount()
                + ", insufficient-context=" + digest.insufficientContextCount()
                + ", high-severity=" + digest.highSeverityCount()
                + ". Digest does not claim Task PASS/FAIL.";
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.HISTORICAL_REVIEW_DIGEST,
                BusinessWarningCategory.ADVISORY,
                eventKey,
                "Tóm tắt review commit lịch sử",
                evidence,
                null,
                team == null ? null : team.getId(),
                projectId,
                null,
                null,
                null,
                null,
                warnings.leadersOfOwningTeam(projectId)
        ));
    }

    private void emitNeedsChanges(CommitReviewIntent intent, CommitReviewResultParser.ParsedResult parsed) {
        GitRepo repo = intent.getRepo();
        UUID projectId = repo.getProject().getId();
        Team team = teams.findByProjectId(projectId).orElse(null);
        String policy = intent.getReviewPolicyVersion() == null ? "unknown" : intent.getReviewPolicyVersion();
        String eventKey = "review:" + projectId + ":" + repo.getId() + ":"
                + intent.getShaHash() + ":COMMIT_REVIEW_NEEDS_CHANGES:" + policy;
        List<BusinessWarningService.Recipient> recipients = new ArrayList<>(warnings.leadersOfOwningTeam(projectId));
        authorRecipient(intent).ifPresent(recipients::add);
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.COMMIT_REVIEW_NEEDS_CHANGES,
                BusinessWarningCategory.CONFIRMED,
                eventKey,
                "Commit cần chỉnh sửa",
                "LIVE TASK_LINKED review returned NEEDS_CHANGES for commit "
                        + shortSha(intent.getShaHash()) + ".",
                null,
                team == null ? null : team.getId(),
                projectId,
                null,
                authorRecipient(intent).map(BusinessWarningService.Recipient::profileId).orElse(null),
                intent.getShaHash(),
                null,
                List.copyOf(recipients)
        ));
    }

    private void emitUnlinkedAdvisory(CommitReviewIntent intent, CommitReviewResultParser.ParsedResult parsed) {
        GitRepo repo = intent.getRepo();
        UUID projectId = repo.getProject().getId();
        Team team = teams.findByProjectId(projectId).orElse(null);
        String policy = intent.getReviewPolicyVersion() == null ? "unknown" : intent.getReviewPolicyVersion();
        String eventKey = "review:" + projectId + ":" + repo.getId() + ":"
                + intent.getShaHash() + ":UNLINKED_COMMIT_ADVISORY:" + policy;
        List<BusinessWarningService.Recipient> recipients = new ArrayList<>(warnings.leadersOfOwningTeam(projectId));
        authorRecipient(intent).ifPresent(recipients::add);
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.UNLINKED_COMMIT_ADVISORY,
                BusinessWarningCategory.ADVISORY,
                eventKey,
                "Commit chưa chứng minh truy vết Task",
                "LIVE UNLINKED_ADVISORY: TRACEABILITY_STATUS=NOT_PROVEN, TASK_ALIGNMENT=NOT_EVALUATED, verdict=ADVISORY_ONLY for commit "
                        + shortSha(intent.getShaHash()) + ".",
                null,
                team == null ? null : team.getId(),
                projectId,
                null,
                authorRecipient(intent).map(BusinessWarningService.Recipient::profileId).orElse(null),
                intent.getShaHash(),
                null,
                List.copyOf(recipients)
        ));
    }

    private Optional<BusinessWarningService.Recipient> authorRecipient(CommitReviewIntent intent) {
        if (intent.getCommit() == null) {
            return Optional.empty();
        }
        return warnings.uniqueActiveGithubAuthor(intent.getCommit().getAuthorExternalId());
    }

    private String shortSha(String sha) {
        if (sha == null || sha.length() < 7) {
            return sha == null ? "" : sha;
        }
        return sha.substring(0, 7);
    }

    public record HistoricalDigest(
            String day,
            long reviewedCount,
            long codeRiskCount,
            long poorMessageCount,
            long insufficientContextCount,
            long highSeverityCount
    ) {
    }
}
