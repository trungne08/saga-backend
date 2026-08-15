package com.saga.be.service;

import com.saga.be.dto.request.ContributionConfigModeRequest;
import com.saga.be.dto.request.ContributionOverrideDecisionRequest;
import com.saga.be.dto.request.CourseContributionSliceWeightRequest;
import com.saga.be.dto.request.CourseContributionSliceWeightUpdateRequest;
import com.saga.be.dto.response.ContributionConfigModeResponse;
import com.saga.be.dto.response.CourseContributionSliceWeightRequestResponse;
import com.saga.be.dto.response.CourseContributionSliceWeightResponse;
import com.saga.be.dto.response.CourseTeamContributionWeightResponse;
import com.saga.be.dto.response.CourseTeamContributionWeightsResponse;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.ProjectType;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.ContributionConfigMode;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PolicyOverrideRequestRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.contribution.ContributionSliceWeights;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourseContributionWeightService {

    private static final String COURSE_SLICE_WEIGHT_OVERRIDE_TYPE = "COURSE_SLICE_WEIGHT_OVERRIDE";
    private static final double EXPECTED_WEIGHT_TOTAL = 100.0;
    private static final double WEIGHT_TOLERANCE = 0.01;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final CourseRepository courseRepository;
    private final PolicyOverrideRequestRepository policyOverrideRequestRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;
    private final TeamRepository teamRepository;
    private final ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    @Transactional(readOnly = true)
    public CourseContributionSliceWeightResponse getCurrentWeights(SagaPrincipal principal, UUID courseId) {
        Course course = requireReadableCourse(principal, courseId);
        ContributionSliceWeights weights = ContributionSliceWeights.fromCourse(course);
        return toCurrentWeightResponse(course, weights);
    }

    @Transactional
    public CourseContributionSliceWeightResponse updateCurrentWeights(
            SagaPrincipal principal,
            UUID courseId,
            CourseContributionSliceWeightUpdateRequest request
    ) {
        Course course = requireLecturerOwnedCourse(principal, courseId);
        ContributionSliceWeights requestedWeights = validateDirectWeights(request);
        course.setCodeContributionWeight(requestedWeights.codeValue());
        course.setTestContributionWeight(requestedWeights.testValue());
        course.setDocumentContributionWeight(requestedWeights.documentValue());
        course.setResearchContributionWeight(requestedWeights.researchValue());
        Course saved = courseRepository.save(course);
        return toCurrentWeightResponse(saved, ContributionSliceWeights.fromCourse(saved));
    }

    /**
     * Switches the Course's active {@link ContributionConfigMode}. Activating {@code TEAM} mode
     * requires every current Team in the Course to already have a valid exact Project+Team
     * override ({@link ProjectGroupWeightConfig}); if any Team is missing one, the mode is not
     * changed and the request fails closed (no partial/mixed activation).
     */
    @Transactional
    public ContributionConfigModeResponse switchConfigMode(
            SagaPrincipal principal,
            UUID courseId,
            ContributionConfigModeRequest request
    ) {
        Course course = requireLecturerOwnedCourse(principal, courseId);
        if (request == null || request.mode() == null) {
            throw IntegrationException.invalid("CONTRIBUTION_CONFIG_MODE_REQUIRED", "mode is required");
        }
        if (request.mode() == ContributionConfigMode.TEAM) {
            requireAllCurrentTeamsHaveValidOverride(courseId);
        }
        course.setContributionConfigMode(request.mode());
        Course saved = courseRepository.save(course);
        return new ContributionConfigModeResponse(saved.getId(), saved.getContributionConfigMode());
    }

    @Transactional(readOnly = true)
    public CourseTeamContributionWeightsResponse getTeamWeights(SagaPrincipal principal, UUID courseId) {
        Course course = requireReadableCourse(principal, courseId);
        List<Team> teams = teamRepository.findByCourseId(courseId);
        List<CourseTeamContributionWeightResponse> teamRows = teams.stream()
                .map(team -> teamWeightRow(course, team))
                .toList();
        return new CourseTeamContributionWeightsResponse(course.getId(), course.getContributionConfigMode(), teamRows);
    }

    private CourseTeamContributionWeightResponse teamWeightRow(Course course, Team team) {
        Project project = team.getProject();
        ProjectType projectType = project == null ? null : project.getProjectType();
        if (course.getContributionConfigMode() == ContributionConfigMode.COURSE) {
            ContributionSliceWeights courseWeights = ContributionSliceWeights.fromCourse(course);
            return new CourseTeamContributionWeightResponse(
                    team.getId(), team.getName(),
                    project == null ? null : project.getId(), project == null ? null : project.getName(),
                    projectType == null ? null : projectType.getId(),
                    projectType == null ? null : projectType.getCode(),
                    projectType == null ? null : projectType.getName(),
                    "COURSE",
                    courseWeights.codeValue(), courseWeights.testValue(),
                    courseWeights.documentValue(), courseWeights.researchValue()
            );
        }
        var override = project == null
                ? java.util.Optional.<ProjectGroupWeightConfig>empty()
                : projectGroupWeightConfigRepository.findByProjectId(project.getId())
                        .filter(candidate -> candidate.getTeam() != null && team.getId().equals(candidate.getTeam().getId()));
        if (override.isEmpty()) {
            return new CourseTeamContributionWeightResponse(
                    team.getId(), team.getName(),
                    project == null ? null : project.getId(), project == null ? null : project.getName(),
                    projectType == null ? null : projectType.getId(),
                    projectType == null ? null : projectType.getCode(),
                    projectType == null ? null : projectType.getName(),
                    "TEAM_INCOMPLETE",
                    null, null, null, null
            );
        }
        ProjectGroupWeightConfig config = override.get();
        return new CourseTeamContributionWeightResponse(
                team.getId(), team.getName(),
                project.getId(), project.getName(),
                projectType == null ? null : projectType.getId(),
                projectType == null ? null : projectType.getCode(),
                projectType == null ? null : projectType.getName(),
                "TEAM",
                toPercent(config.getCodeWeight()), toPercent(config.getTestWeight()),
                toPercent(config.getDocumentWeight()), toPercent(config.getResearchWeight())
        );
    }

    private Double toPercent(BigDecimal ratio) {
        return ratio == null ? null : ratio.multiply(ONE_HUNDRED, MathContext.DECIMAL64).doubleValue();
    }

    private void requireAllCurrentTeamsHaveValidOverride(UUID courseId) {
        List<Team> teams = teamRepository.findByCourseId(courseId);
        for (Team team : teams) {
            Project project = team.getProject();
            if (project == null) {
                throw IntegrationException.conflict(
                        "TEAM_MODE_CONFIGURATION_INCOMPLETE",
                        "Team " + team.getId() + " has no Project and cannot have a weight override"
                );
            }
            ProjectGroupWeightConfig config = projectGroupWeightConfigRepository.findByProjectId(project.getId())
                    .filter(candidate -> candidate.getTeam() != null && team.getId().equals(candidate.getTeam().getId()))
                    .orElseThrow(() -> IntegrationException.conflict(
                            "TEAM_MODE_CONFIGURATION_INCOMPLETE",
                            "Team " + team.getId() + " has no weight override configured"
                    ));
            BigDecimal total = config.getCodeWeight().add(config.getTestWeight())
                    .add(config.getDocumentWeight()).add(config.getResearchWeight());
            if (total.compareTo(BigDecimal.ONE) != 0) {
                throw IntegrationException.conflict(
                        "TEAM_MODE_CONFIGURATION_INCOMPLETE",
                        "Team " + team.getId() + " has an invalid weight override"
                );
            }
        }
    }

    @Transactional
    public CourseContributionSliceWeightRequestResponse requestWeightChange(
            UUID courseId,
            CourseContributionSliceWeightRequest request
    ) {
        if (request == null || request.lecturerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lecturer is required");
        }
        LegacyTriad requestedWeights = validateRequestedWeights(request);
        Course course = requireCourse(courseId);
        Lecturer lecturer = lecturerRepository.findById(request.lecturerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));
        if (course.getInstructor() == null
                || !Objects.equals(course.getInstructor().getId(), lecturer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the assigned lecturer can request slice weight changes for this course"
            );
        }

        PolicyOverrideRequest savedRequest = policyOverrideRequestRepository.save(
                PolicyOverrideRequest.builder()
                        .clazz(course.getClazz())
                        .lecturer(lecturer)
                        .targetConfigId(course.getId())
                        .type(COURSE_SLICE_WEIGHT_OVERRIDE_TYPE)
                        .proposedCodeWeight((float) requestedWeights.code())
                        .proposedDocumentWeight((float) requestedWeights.document())
                        .proposedDesignWeight((float) requestedWeights.design())
                        .reason(request.reason())
                        .status(PolicyOverrideStatus.PENDING)
                        .build()
        );
        return toRequestResponse(savedRequest, course);
    }

    @Transactional(readOnly = true)
    public List<CourseContributionSliceWeightRequestResponse> listWeightChangeRequests(
            SagaPrincipal principal,
            PolicyOverrideStatus status,
            UUID courseId
    ) {
        List<PolicyOverrideRequest> requests;
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            if (courseId != null) {
                requireCourse(courseId);
                requests = policyOverrideRequestRepository.findByTypeAndTargetConfigIdOrderByCreatedAtDesc(
                        COURSE_SLICE_WEIGHT_OVERRIDE_TYPE,
                        courseId
                );
            } else if (status != null) {
                requests = policyOverrideRequestRepository.findByTypeAndStatusOrderByCreatedAtDesc(
                        COURSE_SLICE_WEIGHT_OVERRIDE_TYPE,
                        status
                );
            } else {
                requests = policyOverrideRequestRepository.findByTypeOrderByCreatedAtDesc(
                        COURSE_SLICE_WEIGHT_OVERRIDE_TYPE
                );
            }
        } else if (principal.applicationRole() == ApplicationRole.LECTURER) {
            if (courseId != null) {
                Course course = requireCourse(courseId);
                if (course.getInstructor() == null
                        || !Objects.equals(course.getInstructor().getId(), principal.localProfileId())) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "You can only view override requests for your own courses"
                    );
                }
                requests = policyOverrideRequestRepository.findByTypeAndTargetConfigIdOrderByCreatedAtDesc(
                        COURSE_SLICE_WEIGHT_OVERRIDE_TYPE,
                        courseId
                );
            } else {
                requests = policyOverrideRequestRepository.findByTypeAndLecturer_IdOrderByCreatedAtDesc(
                        COURSE_SLICE_WEIGHT_OVERRIDE_TYPE,
                        principal.localProfileId()
                );
            }
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only admin or lecturer can view course slice weight override requests"
            );
        }
        return requests.stream()
                .filter(request -> status == null || request.getStatus() == status)
                .map(request -> toRequestResponse(
                        request,
                        request.getTargetConfigId() == null
                                ? null
                                : courseRepository.findById(request.getTargetConfigId()).orElse(null)
                ))
                .toList();
    }

    @Transactional
    public CourseContributionSliceWeightRequestResponse decideWeightChangeRequest(
            UUID requestId,
            ContributionOverrideDecisionRequest request
    ) {
        if (request == null || request.decision() == null || request.decision().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision is required");
        }
        PolicyOverrideRequest overrideRequest = policyOverrideRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Override request not found"));
        if (!COURSE_SLICE_WEIGHT_OVERRIDE_TYPE.equals(overrideRequest.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Override request is not a course slice weight request");
        }
        if (overrideRequest.getStatus() != PolicyOverrideStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Override request has already been resolved");
        }

        PolicyOverrideStatus status;
        try {
            status = PolicyOverrideStatus.valueOf(request.decision().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }
        if (status == PolicyOverrideStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }

        Admin admin = request.adminId() == null
                ? null
                : adminRepository.findById(request.adminId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        Course course = requireCourse(overrideRequest.getTargetConfigId());
        if (status == PolicyOverrideStatus.APPROVED) {
            // Legacy flow only ever proposed Code/Document/Design (the retired triad) and is not
            // extended with test/research (no consumer). Code/Document apply directly to the
            // active columns; the legacy "design" proposal is preserved verbatim in the retained
            // design_contribution_weight column (historical, inactive) rather than discarded or
            // redistributed into the new criteria — no conversion formula is invented.
            // ContributionSliceWeights.fromCourse always renormalizes whatever is stored, so a
            // stored active sum below 100 here does not break Contribution calculation.
            course.setCodeContributionWeight(floatValue(overrideRequest.getProposedCodeWeight()));
            course.setDocumentContributionWeight(floatValue(overrideRequest.getProposedDocumentWeight()));
            course.setDesignContributionWeight(floatValue(overrideRequest.getProposedDesignWeight()));
            course.setTestContributionWeight(0.0);
            courseRepository.save(course);
        }

        overrideRequest.setStatus(status);
        overrideRequest.setResolvedBy(admin);
        overrideRequest.setResolvedAt(LocalDateTime.now());
        PolicyOverrideRequest savedRequest = policyOverrideRequestRepository.save(overrideRequest);
        return toRequestResponse(savedRequest, course);
    }

    private ContributionSliceWeights validateDirectWeights(CourseContributionSliceWeightUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Code, test, document, and research weights are all required"
            );
        }
        return validateWeightValues(
                request.codeWeight(), request.testWeight(), request.documentWeight(), request.researchWeight()
        );
    }

    // Legacy request/decision flow is deprecated for new FE and intentionally not extended with
    // test/research (no consumer); it keeps validating the original Code/Document/Design triad
    // against PolicyOverrideRequest's fixed proposed* columns.
    private LegacyTriad validateRequestedWeights(CourseContributionSliceWeightRequest request) {
        Double codeWeight = request.codeWeight();
        Double documentWeight = request.documentWeight();
        Double designWeight = request.designWeight();
        if (codeWeight == null || documentWeight == null || designWeight == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Code, document, and design weights are all required"
            );
        }
        if (codeWeight < 0.0 || documentWeight < 0.0 || designWeight < 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slice weights must be non-negative");
        }
        double total = codeWeight + documentWeight + designWeight;
        if (Math.abs(total - EXPECTED_WEIGHT_TOTAL) > WEIGHT_TOLERANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slice weights must add up to 100");
        }
        return new LegacyTriad(codeWeight, documentWeight, designWeight);
    }

    private record LegacyTriad(double code, double document, double design) {
    }

    private ContributionSliceWeights validateWeightValues(
            Double codeWeight,
            Double testWeight,
            Double documentWeight,
            Double researchWeight
    ) {
        if (codeWeight == null || testWeight == null || documentWeight == null || researchWeight == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Code, test, document, and research weights are all required"
            );
        }
        if (codeWeight < 0.0 || testWeight < 0.0 || documentWeight < 0.0 || researchWeight < 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slice weights must be non-negative");
        }
        double total = codeWeight + testWeight + documentWeight + researchWeight;
        if (Math.abs(total - EXPECTED_WEIGHT_TOTAL) > WEIGHT_TOLERANCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slice weights must add up to 100");
        }
        return ContributionSliceWeights.normalizeConfigured(
                BigDecimal.valueOf(codeWeight),
                BigDecimal.valueOf(testWeight),
                BigDecimal.valueOf(documentWeight),
                BigDecimal.valueOf(researchWeight)
        );
    }

    private Course requireReadableCourse(SagaPrincipal principal, UUID courseId) {
        Course course = requireCourse(courseId);
        if (principal == null || principal.localProfileId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return course;
        }
        requireLecturerOwnsCourse(principal, course);
        return course;
    }

    private Course requireLecturerOwnedCourse(SagaPrincipal principal, UUID courseId) {
        Course course = requireCourse(courseId);
        requireLecturerOwnsCourse(principal, course);
        return course;
    }

    private void requireLecturerOwnsCourse(SagaPrincipal principal, Course course) {
        if (principal == null
                || principal.applicationRole() != ApplicationRole.LECTURER
                || principal.localProfileId() == null
                || course.getInstructor() == null
                || !Objects.equals(course.getInstructor().getId(), principal.localProfileId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the assigned lecturer can manage slice weights for this course"
            );
        }
    }

    private Course requireCourse(UUID courseId) {
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is required");
        }
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private CourseContributionSliceWeightResponse toCurrentWeightResponse(
            Course course,
            ContributionSliceWeights weights
    ) {
        return new CourseContributionSliceWeightResponse(
                course.getId(),
                course.getCourseCode(),
                course.getName(),
                weights.codeValue(),
                weights.testValue(),
                weights.documentValue(),
                weights.researchValue()
        );
    }

    private CourseContributionSliceWeightRequestResponse toRequestResponse(
            PolicyOverrideRequest request,
            Course course
    ) {
        return new CourseContributionSliceWeightRequestResponse(
                request.getId(),
                course != null ? course.getId() : request.getTargetConfigId(),
                course != null ? course.getCourseCode() : null,
                course != null ? course.getName() : null,
                request.getLecturer() != null ? request.getLecturer().getId() : null,
                request.getLecturer() != null ? request.getLecturer().getFullName() : null,
                request.getProposedCodeWeight() == null ? 0.0 : request.getProposedCodeWeight(),
                request.getProposedDocumentWeight() == null ? 0.0 : request.getProposedDocumentWeight(),
                request.getProposedDesignWeight() == null ? 0.0 : request.getProposedDesignWeight(),
                request.getReason(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getResolvedAt()
        );
    }

    private double floatValue(Float value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
