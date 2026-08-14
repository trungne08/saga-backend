package com.saga.be.controller;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CourseEarlyWarningQueryService;
import com.saga.be.service.LecturerContributionQueryService;
import com.saga.be.service.LecturerStudentAnalyticsQueryService;
import com.saga.be.service.LecturerTeamAnalyticsQueryService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đánh giá", description = "Phân tích tiến độ và hoạt động dành cho giảng viên.")
@RequestMapping("/api/v1/courses/{courseId}")
@RequiredArgsConstructor
public class LecturerAnalyticsController {

    private final LecturerTeamAnalyticsQueryService teamAnalytics;
    private final LecturerStudentAnalyticsQueryService studentAnalytics;
    private final LecturerContributionQueryService contributionAnalytics;
    private final CourseEarlyWarningQueryService earlyWarningAnalytics;

    @GetMapping("/teams/{teamId}/detail")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem chi tiết nhóm và Project của Course",
            description = "Trả thành viên, Project nullable và danh sách GitHub repository local theo thứ tự ổn định; không gọi GitHub provider."
    )
    public ResponseEntity<LecturerAnalyticsResponses.TeamDetail> teamDetail(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(teamAnalytics.detail(principal, courseId, teamId, pageRequest(page, size)));
    }

    @GetMapping("/teams/{teamId}/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem tổng quan hoạt động của nhóm",
            description = "ADMIN đọc mọi Team; LECTURER đọc Team thuộc Course mình phụ trách; "
                    + "STUDENT có TeamMember role LEADER hoặc MEMBER chỉ đọc exact Team của mình. "
                    + "Trả chuỗi hoạt động theo ngày và tổng hợp theo loại activity; không gọi provider ngoài."
    )
    public ResponseEntity<LecturerAnalyticsResponses.ActivityOverview> overview(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(teamAnalytics.overview(principal, courseId, teamId, startDate, endDate));
    }

    @GetMapping("/students/{studentId}/progress")
    public ResponseEntity<LecturerAnalyticsResponses.StudentProgress> progress(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID studentId) {
        return ResponseEntity.ok(studentAnalytics.progress(principal, courseId, studentId));
    }

    @GetMapping("/students/{studentId}/activities")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<LecturerAnalyticsResponses.StudentActivities> activities(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID studentId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(studentAnalytics.activities(principal, courseId, studentId, pageRequest(page, size)));
    }

    @GetMapping("/students/{studentId}/contribution-detail")
    public ResponseEntity<LecturerAnalyticsResponses.StudentContributionDetail> contributionDetail(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID studentId) {
        return ResponseEntity.ok(contributionAnalytics.get(principal, courseId, studentId));
    }

    @GetMapping("/early-warnings")
    public ResponseEntity<LecturerAnalyticsResponses.EarlyWarnings> earlyWarnings(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId) {
        return ResponseEntity.ok(earlyWarningAnalytics.get(principal, courseId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @GetMapping("/teams/{teamId}/students/{studentId}/interactions")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem mạng tương tác của một sinh viên trong nhóm",
            description = "ADMIN đọc mọi Team; LECTURER đọc Team thuộc Course mình phụ trách; "
                    + "STUDENT có TeamMember role LEADER hoặc MEMBER đọc thành viên trong exact Team của mình. "
                    + "Caller Student không cần trùng target studentId."
    )
    public ResponseEntity<LecturerAnalyticsResponses.StudentInteractionGraph> studentInteractions(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId, @PathVariable UUID studentId) {
        return ResponseEntity.ok(teamAnalytics.studentInteractions(principal, courseId, teamId, studentId));
    }

    @GetMapping("/teams/{teamId}/sprints/{sprintId}/burndown")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem biểu đồ burndown của sprint",
            description = "ADMIN đọc mọi Team; LECTURER đọc Team thuộc Course mình phụ trách; "
                    + "STUDENT có TeamMember role LEADER hoặc MEMBER chỉ đọc exact Team của mình. "
                    + "Sprint phải thuộc Project của Team trong URL."
    )
    public ResponseEntity<LecturerAnalyticsResponses.BurndownChart> burndown(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId, @PathVariable UUID sprintId) {
        return ResponseEntity.ok(teamAnalytics.burndown(principal, courseId, teamId, sprintId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @GetMapping("/teams/{teamId}/heatmap")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem heatmap hoạt động của nhóm",
            description = "ADMIN đọc mọi Team; LECTURER đọc Team thuộc Course mình phụ trách; "
                    + "STUDENT có TeamMember role LEADER hoặc MEMBER chỉ đọc exact Team của mình. "
                    + "studentId tùy chọn phải là thành viên của exact Team đó."
    )
    public ResponseEntity<LecturerAnalyticsResponses.ActivityHeatmap> heatmap(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId, @RequestParam(required = false) UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(teamAnalytics.heatmap(principal, courseId, teamId, studentId, startDate, endDate));
    }

    @GetMapping("/teams/{teamId}/sprints/velocity")
    public ResponseEntity<LecturerAnalyticsResponses.SprintVelocity> velocity(
            @AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID courseId,
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(teamAnalytics.velocity(principal, courseId, teamId));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page phải không âm và size phải từ 1 đến 100");
        }
        return PageRequest.of(page, size);
    }
}
