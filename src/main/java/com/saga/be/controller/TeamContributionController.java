package com.saga.be.controller;

import com.saga.be.dto.request.ContributionOverrideRequest;
import com.saga.be.dto.response.ContributionOverrideResponse;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.TeamContributionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamContributionController {

    private final TeamContributionService teamContributionService;

    @GetMapping("/{teamId}/contribution-evaluation")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<TeamContributionEvaluationResponse> getContributionEvaluation(
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamContributionService.evaluate(teamId));
    }

    @PostMapping("/{teamId}/contribution-override")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<ContributionOverrideResponse> requestContributionOverride(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @RequestBody ContributionOverrideRequest request
    ) {
        return ResponseEntity.ok(teamContributionService.requestContributionOverride(principal, teamId, request));
    }
}
