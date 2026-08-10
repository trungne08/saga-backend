package com.saga.be.controller;

import com.saga.be.dto.response.MyCourseTeamMembersResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.TeamRosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/courses/{courseId}/team/members")
@RequiredArgsConstructor
@Tag(name = "Nhóm", description = "Xem thành viên nhóm của sinh viên trong khóa học.")
public class MyCourseTeamController {

    private final TeamRosterService teamRosterService;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Xem thành viên nhóm của tôi trong khóa học",
            description = "STUDENT self-scoped endpoint. The backend resolves teamId from the session-backed "
                    + "SagaPrincipal and courseId; clients must not send teamId. GET does not require CSRF.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resolved team and paginated roster"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Only STUDENT may use this self-scoped endpoint"),
            @ApiResponse(responseCode = "404", description = "Course or the student's team in the course was not found"),
            @ApiResponse(responseCode = "409", description = "Legacy data has multiple teams for the student in the course")
    })
    public ResponseEntity<MyCourseTeamMembersResponse> getMyCourseTeamMembers(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Parameter(description = "Course UUID") @PathVariable UUID courseId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, maximum 100")
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be non-negative and size must be between 1 and 100"
            );
        }
        return ResponseEntity.ok(teamRosterService.getCurrentStudentTeamMembers(
                principal,
                courseId,
                PageRequest.of(page, size)
        ));
    }
}
