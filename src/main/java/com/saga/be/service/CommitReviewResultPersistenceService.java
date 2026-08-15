package com.saga.be.service;

import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitRepo;
import com.saga.be.repository.CommitReviewResultRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommitReviewResultPersistenceService {

    private final CommitReviewResultRepository results;
    private final ObjectMapper objectMapper;

    public CommitReviewResultPersistenceService(
            CommitReviewResultRepository results,
            ObjectMapper objectMapper
    ) {
        this.results = results;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CommitReviewResult> persistOnce(
            CommitReviewIntent intent,
            UUID jobId,
            CommitReviewResultParser.ParsedResult parsed
    ) {
        if (intent == null || intent.getId() == null || jobId == null || parsed == null) {
            return Optional.empty();
        }
        Optional<CommitReviewResult> existing = results.findByIntentId(intent.getId());
        if (existing.isPresent()) {
            return existing;
        }
        if (results.findByAiJobId(jobId).isPresent()) {
            return results.findByAiJobId(jobId);
        }
        GitRepo repo = intent.getRepo();
        if (repo == null || repo.getProject() == null || intent.getCommit() == null) {
            return Optional.empty();
        }
        CommitReviewResult saved = results.saveAndFlush(CommitReviewResult.builder()
                .intent(intent)
                .aiJobId(jobId)
                .projectId(repo.getProject().getId())
                .repo(repo)
                .commit(intent.getCommit())
                .shaHash(intent.getShaHash())
                .policyVersion(intent.getReviewPolicyVersion() == null
                        ? parsed.policyVersion()
                        : intent.getReviewPolicyVersion())
                .reviewMode(parsed.reviewMode())
                .traceabilityStatus(parsed.traceabilityStatus())
                .messageQuality(parsed.messageQuality())
                .codeQuality(parsed.codeQuality())
                .inferredFunctionLabel(parsed.inferredFunctionLabel())
                .inferredFunctionConfidence(parsed.inferredFunctionConfidence())
                .taskAlignment(parsed.taskAlignment())
                .verdictEligible(parsed.verdictEligible())
                .verdict(parsed.verdict())
                .overallStatus(parsed.overallStatus())
                .schemaVersion(parsed.schemaVersion())
                .findingsJson(writeJson(parsed.findings()))
                .evidenceRefsJson(writeJson(parsed.evidenceRefs()))
                .completedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
        return Optional.of(saved);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.List.of() : value);
        } catch (RuntimeException exception) {
            return "[]";
        }
    }
}
