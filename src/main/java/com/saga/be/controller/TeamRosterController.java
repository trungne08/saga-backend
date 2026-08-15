package com.saga.be.controller;

import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.TeamRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/teams/{teamId}/members")
@RequiredArgsConstructor
@Validated
@Tag(name = "Nhóm", description = "Xem danh sách thành viên nhóm.")
public class TeamRosterController {

    private final TeamRosterService teamRosterService;

    @GetMapping
    @Operation(summary = "Xem danh sách thành viên của nhóm trong khóa học")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated team roster"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to view this roster"),
            @ApiResponse(responseCode = "404", description = "Team does not exist in the specified course")
    })
    public ResponseEntity<Page<TeamMemberResponse>> getMembers(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Parameter(description = "Course UUID") @PathVariable UUID courseId,
            @Parameter(description = "Team UUID") @PathVariable UUID teamId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, maximum 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(teamRosterService.getMembers(
                principal,
                courseId,
                teamId,
                PageRequest.of(page, size)
        ));
    }
}
