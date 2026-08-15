package com.saga.be.controller;

import com.saga.be.dto.request.ContributionOverrideRequest;
import com.saga.be.dto.response.ContributionOverrideResponse;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionGraphResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.TeamContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đóng góp", description = "Xem đánh giá và gửi điều chỉnh đóng góp.")
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamContributionController {

    private final TeamContributionService teamContributionService;

    @GetMapping("/{teamId}/contribution-evaluation")
    @PreAuthorize("hasAnyRole('LECTURER', 'STUDENT')")
    @Operation(
            summary = "View a team's current contribution evaluation",
            description = "LECTURER only a Team in a Course they instruct; "
                    + "STUDENT only when their exact TeamMember role for this Team is LEADER. "
                    + "ADMIN, MEMBER and MENTOR are not permitted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current contribution aggregate"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to read this Team's contribution"),
            @ApiResponse(responseCode = "404", description = "Team does not exist")
    })
    public ResponseEntity<TeamContributionEvaluationResponse> getContributionEvaluation(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamContributionService.evaluate(principal, teamId));
    }

    @GetMapping("/{teamId}/contribution-graph")
    @PreAuthorize("hasAnyRole('LECTURER', 'STUDENT')")
    @Operation(
            summary = "View a team's contribution flowchart",
            description = "Same authorization as contribution-evaluation: LECTURER of the Course "
                    + "and STUDENT exact Team LEADER. ADMIN, MEMBER and MENTOR are not permitted. "
                    + "Nodes and edges use the SAGA mixer (CODE/TEST/DOCUMENT/RESEARCH weight "
                    + "ratios and peer as team-star share), not mockup multipliers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contribution flowchart nodes and edges"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to read this Team's contribution"),
            @ApiResponse(responseCode = "404", description = "Team does not exist")
    })
    public ResponseEntity<TeamContributionGraphResponse> getContributionGraph(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(teamContributionService.graph(principal, teamId));
    }

    @PostMapping("/{teamId}/contribution-override")
    @PreAuthorize("hasAnyRole('LECTURER')")
    public ResponseEntity<ContributionOverrideResponse> requestContributionOverride(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @RequestBody ContributionOverrideRequest request
    ) {
        return ResponseEntity.ok(teamContributionService.requestContributionOverride(principal, teamId, request));
    }
}
