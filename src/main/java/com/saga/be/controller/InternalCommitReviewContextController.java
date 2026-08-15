package com.saga.be.controller;

import com.saga.be.dto.response.InternalCommitReviewContextResponse;
import com.saga.be.service.CommitReviewContextService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequestMapping("/internal/ai/v1/projects/{projectId}/github/repositories/{repositoryId}/commits")
public class InternalCommitReviewContextController {

    private final CommitReviewContextService contexts;

    public InternalCommitReviewContextController(CommitReviewContextService contexts) {
        this.contexts = contexts;
    }

    @GetMapping("/{commitSha}/review-context")
    public InternalCommitReviewContextResponse context(
            @PathVariable UUID projectId,
            @PathVariable long repositoryId,
            @PathVariable String commitSha
    ) {
        return contexts.context(projectId, repositoryId, commitSha);
    }
}
