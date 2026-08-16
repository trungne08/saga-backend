package com.saga.be.service;

import com.saga.be.dto.request.ContributionOverrideRequest;
import com.saga.be.dto.response.ContributionOverrideResponse;
import com.saga.be.dto.response.ContributionWarning;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionGraphResponse;
import com.saga.be.dto.response.TeamContributionMemberResponse;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PolicyOverrideRequest;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskAttachment;
import com.saga.be.entity.TaskWebLink;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.PolicyOverrideStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.PolicyOverrideRequestRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskAttachmentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TaskWebLinkRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.contribution.ContributionCriterion;
import com.saga.be.service.contribution.ContributionSliceWeightResolver;
import com.saga.be.service.contribution.ContributionSliceWeights;
import com.saga.be.service.contribution.SprintFirstContributionMixer;
import com.saga.be.service.contribution.TaskContributionClassifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
// Keeps the legacy contribution API backed by the current local aggregates.
public class TeamContributionService {

    private static final String TEAM_CONTRIBUTION_OVERRIDE_TYPE = "TEAM_CONTRIBUTION_OVERRIDE";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;
    private final CommitDataRepository commitDataRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final SprintRepository sprintRepository;
    private final PolicyOverrideRequestRepository policyOverrideRequestRepository;
    private final LecturerRepository lecturerRepository;
    private final ContributionSliceWeightResolver sliceWeightResolver;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskWebLinkRepository taskWebLinkRepository;

    @Transactional(readOnly = true)
    public TeamContributionEvaluationResponse evaluate(SagaPrincipal principal, UUID teamId) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        requireContributionReadAccess(principal, team);
        return evaluate(teamId, team);
    }

    @Transactional(readOnly = true)
    TeamContributionEvaluationResponse evaluate(UUID teamId) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        return evaluate(teamId, team);
    }

    @Transactional(readOnly = true)
    public TeamContributionGraphResponse graph(SagaPrincipal principal, UUID teamId, UUID sprintId) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        requireContributionReadAccess(principal, team);
        return graph(teamId, team, sprintId);
    }

    @Transactional(readOnly = true)
    TeamContributionGraphResponse graph(UUID teamId) {
        return graph(teamId, (UUID) null);
    }

    @Transactional(readOnly = true)
    TeamContributionGraphResponse graph(UUID teamId, UUID sprintId) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        return graph(teamId, team, sprintId);
    }

    private TeamContributionGraphResponse graph(UUID teamId, Team team, UUID sprintId) {
        TeamContributionEvaluationResponse evaluation = evaluate(teamId, team);
        UUID projectId = evaluation.projectId();
        Sprint sprintFilter = resolveGraphSprint(projectId, sprintId);
        ContributionSliceWeights weights = team.getCourse() == null
                ? ContributionSliceWeights.fromCourse(null)
                : sliceWeightResolver.resolve(team);
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        Map<UUID, String> roleByStudent = new HashMap<>();
        for (TeamMember member : members) {
            if (member.getStudent() == null || member.getStudent().getId() == null) {
                continue;
            }
            RoleInTeam role = member.getRoleInTeam();
            roleByStudent.put(member.getStudent().getId(), role == null ? null : role.name());
        }

        List<TeamContributionGraphResponse.ContributionGraphNode> nodes = new ArrayList<>();
        for (ContributionCriterion criterion : ContributionCriterion.values()) {
            double percent = weightPercent(weights, criterion);
            double ratio = percent / 100.0;
            nodes.add(new TeamContributionGraphResponse.ContributionGraphNode(
                    criterionNodeId(criterion),
                    "CRITERION",
                    criterion.name(),
                    ratio,
                    percent,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            ));
        }
        UUID sprintFilterId = sprintFilter == null ? null : sprintFilter.getId();
        for (TeamContributionMemberResponse member : evaluation.members()) {
            StudentGraphScores scores = scoresForGraph(member, sprintFilterId);
            nodes.add(new TeamContributionGraphResponse.ContributionGraphNode(
                    studentNodeId(member.studentId()),
                    "STUDENT",
                    null,
                    null,
                    null,
                    member.studentId(),
                    member.fullName(),
                    member.studentCode(),
                    roleByStudent.get(member.studentId()),
                    scores.sliceScore(),
                    scores.peerCoefficient(),
                    scores.adjustedScore(),
                    scores.finalContributionPercentage(),
                    member.warnings()
            ));
        }

        List<TeamContributionGraphResponse.ContributionGraphEdge> edges = List.of();
        if (projectId != null) {
            edges = buildCriterionEdges(projectId, weights, sprintFilterId);
        }
        return new TeamContributionGraphResponse(
                teamId,
                projectId,
                evaluation.evaluatedAt(),
                TeamContributionGraphResponse.FORMULA,
                new TeamContributionGraphResponse.ContributionGraphWeights(
                        weights.codeRatio().doubleValue(),
                        weights.testRatio().doubleValue(),
                        weights.documentRatio().doubleValue(),
                        weights.researchRatio().doubleValue(),
                        weights.codeValue(),
                        weights.testValue(),
                        weights.documentValue(),
                        weights.researchValue()
                ),
                sprintFilter == null ? null : sprintFilter.getId(),
                sprintFilter == null ? null : sprintFilter.getName(),
                nodes,
                edges
        );
    }

    private Sprint resolveGraphSprint(UUID projectId, UUID sprintId) {
        if (sprintId == null) {
            return null;
        }
        if (projectId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found");
        }
        return sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
    }

    private static StudentGraphScores scoresForGraph(
            TeamContributionMemberResponse member,
            UUID sprintId
    ) {
        if (sprintId == null) {
            double slice = member.sliceScore();
            double peer = member.peerReviewScore();
            return new StudentGraphScores(
                    slice,
                    peer,
                    slice * peer,
                    member.finalContributionPercentage()
            );
        }
        for (TeamContributionMemberResponse.SprintContributionBreakdown breakdown : member.sprintBreakdowns()) {
            if (sprintId.equals(breakdown.sprintId())) {
                double slice = breakdown.sliceScore();
                double peer = breakdown.retrospectiveMultiplier();
                return new StudentGraphScores(
                        slice,
                        peer,
                        slice * peer,
                        breakdown.contributionPercentage()
                );
            }
        }
        return new StudentGraphScores(0.0, 1.0, 0.0, 0.0);
    }

    private record StudentGraphScores(
            double sliceScore,
            double peerCoefficient,
            double adjustedScore,
            double finalContributionPercentage
    ) {
    }

    private List<TeamContributionGraphResponse.ContributionGraphEdge> buildCriterionEdges(
            UUID projectId,
            ContributionSliceWeights weights,
            UUID sprintFilterId
    ) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        Map<UUID, Integer> evidenceByTask = evidenceCounts(projectId);
        Map<String, List<TeamContributionGraphResponse.ContributionGraphTask>> tasksByEdge = new LinkedHashMap<>();
        Map<String, Double> storyPointsByEdge = new HashMap<>();
        Map<String, ContributionCriterion> criterionByEdge = new HashMap<>();
        Map<String, UUID> studentByEdge = new HashMap<>();
        for (Task task : tasks) {
            if (task == null
                    || task.getId() == null
                    || !TaskStatus.DONE.equals(task.getStatus())
                    || task.getAssignee() == null
                    || task.getAssignee().getId() == null
                    || task.getSprint() == null
                    || task.getSprint().getId() == null
                    || (sprintFilterId != null && !sprintFilterId.equals(task.getSprint().getId()))) {
                continue;
            }
            Optional<ContributionCriterion> classified = TaskContributionClassifier.classify(task);
            if (classified.isEmpty()) {
                continue;
            }
            ContributionCriterion criterion = classified.get();
            int evidence = evidenceByTask.getOrDefault(task.getId(), 0);
            if (!TaskContributionClassifier.storyPointsRecognized(criterion, evidence)) {
                continue;
            }
            UUID studentId = task.getAssignee().getId();
            double storyPoints = task.getStoryPoint() != null ? task.getStoryPoint().doubleValue() : 1.0;
            if (storyPoints <= 0.0) {
                continue;
            }
            String edgeId = edgeId(criterion, studentId);
            storyPointsByEdge.merge(edgeId, storyPoints, Double::sum);
            criterionByEdge.put(edgeId, criterion);
            studentByEdge.put(edgeId, studentId);
            Sprint sprint = task.getSprint();
            tasksByEdge.computeIfAbsent(edgeId, ignored -> new ArrayList<>()).add(
                    new TeamContributionGraphResponse.ContributionGraphTask(
                            task.getId(),
                            task.getTitle(),
                            task.getExternalKey(),
                            sprint.getId(),
                            sprint.getName(),
                            storyPoints
                    )
            );
        }
        List<TeamContributionGraphResponse.ContributionGraphEdge> edges = new ArrayList<>();
        List<String> edgeIds = new ArrayList<>(tasksByEdge.keySet());
        edgeIds.sort(Comparator.naturalOrder());
        for (String edgeId : edgeIds) {
            ContributionCriterion criterion = criterionByEdge.get(edgeId);
            double storyPoints = storyPointsByEdge.getOrDefault(edgeId, 0.0);
            List<TeamContributionGraphResponse.ContributionGraphTask> edgeTasks = tasksByEdge.get(edgeId);
            edgeTasks.sort(Comparator
                    .comparing((TeamContributionGraphResponse.ContributionGraphTask item) ->
                            item.externalKey() == null ? "" : item.externalKey())
                    .thenComparing(item -> item.taskId().toString()));
            edges.add(new TeamContributionGraphResponse.ContributionGraphEdge(
                    edgeId,
                    criterionNodeId(criterion),
                    studentNodeId(studentByEdge.get(edgeId)),
                    criterion.name(),
                    storyPoints,
                    storyPoints * weightPercent(weights, criterion) / 100.0,
                    edgeTasks
            ));
        }
        return edges;
    }

    private static String criterionNodeId(ContributionCriterion criterion) {
        return "criterion:" + criterion.name();
    }

    private static String studentNodeId(UUID studentId) {
        return "student:" + studentId;
    }

    private static String edgeId(ContributionCriterion criterion, UUID studentId) {
        return "edge:" + criterion.name() + ":" + studentId;
    }

    private static double weightPercent(ContributionSliceWeights weights, ContributionCriterion criterion) {
        return switch (criterion) {
            case CODE -> weights.codeValue();
            case TEST -> weights.testValue();
            case DOCUMENT -> weights.documentValue();
            case RESEARCH -> weights.researchValue();
        };
    }

    private TeamContributionEvaluationResponse evaluate(UUID teamId, Team team) {
        UUID projectId = team.getProject() != null ? team.getProject().getId() : null;
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);

        List<TeamContributionMemberResponse> result = new ArrayList<>();
        if (projectId == null || members.isEmpty()) {
            return new TeamContributionEvaluationResponse(teamId, projectId, LocalDateTime.now(), result);
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        Map<UUID, Integer> attachmentCountByTaskId = evidenceCounts(projectId);
        List<UUID> studentIds = members.stream()
                .map(TeamMember::getStudent)
                .filter(student -> student != null && student.getId() != null)
                .map(Student::getId)
                .toList();
        List<PeerReview> peerReviews = studentIds.isEmpty()
                ? List.of()
                : peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                        studentIds,
                        projectId
                );
        List<Sprint> sprints = resolveSprints(projectId, tasks, peerReviews);
        Map<UUID, Double> approvedOverrideByStudent = loadApprovedOverrides(team, studentIds);
        Map<UUID, Double> peerScoreByStudent = totalPeerScoreByStudent(peerReviews);
        Map<UUID, Map<UUID, Double>> peerScoreBySprint = totalPeerScoreBySprint(peerReviews);
        double totalPeerScore = peerScoreByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> peerCoefficientByStudent = new HashMap<>();
        for (UUID studentId : studentIds) {
            peerCoefficientByStudent.put(
                    studentId,
                    totalPeerScore > 0.0
                            ? peerScoreByStudent.getOrDefault(studentId, 0.0) / totalPeerScore
                            : 1.0
            );
        }

        Map<UUID, Double> codeScoreByStudent = new HashMap<>();
        Map<UUID, Double> testScoreByStudent = new HashMap<>();
        Map<UUID, Double> documentScoreByStudent = new HashMap<>();
        Map<UUID, Double> researchScoreByStudent = new HashMap<>();
        Map<UUID, Double> adjustedSprintScoreByStudent = new HashMap<>();
        Map<UUID, List<TeamContributionMemberResponse.SprintContributionBreakdown>>
                sprintBreakdownsByStudent = new HashMap<>();
        Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint = new HashMap<>();
        Map<UUID, Map<UUID, Double>> taskScoreByStudentSprint = new HashMap<>();

        Map<UUID, Integer> taskCountByStudent = new HashMap<>();
        Map<UUID, Integer> commitCountByStudent = new HashMap<>();
        Map<UUID, Integer> peerReviewCountByStudent = new HashMap<>();
        Map<UUID, Map<UUID, List<PeerReview>>> peerReviewsByStudentAndSprint =
                groupPeerReviews(peerReviews);
        List<UUID> memberStudentIds = new ArrayList<>();

        for (TeamMember member : members) {
            Student student = member.getStudent();
            if (student == null || student.getId() == null) {
                continue;
            }
            UUID studentId = student.getId();
            memberStudentIds.add(studentId);

            // Product decision: when a commit is linked to a Task, the Task is the sole numeric
            // Contribution authority — the commit is supporting/provenance evidence only and
            // never mints additional score of its own (no CODE/TEST/DOCUMENT/RESEARCH bucket is
            // incremented here). commitCountByStudent below still reflects task-linked commit
            // evidence for evidenceCount/warnings purposes; only the scoring effect is removed.
            List<CommitData> studentCommits = commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(studentId, projectId);
            commitCountByStudent.put(studentId, studentCommits.size());
            codeScoreByStudent.put(studentId, 0.0);
            testScoreByStudent.put(studentId, 0.0);
            documentScoreByStudent.put(studentId, 0.0);
            researchScoreByStudent.put(studentId, 0.0);

            List<Task> completedTasks = tasks.stream()
                    .filter(task -> studentId.equals(task.getAssignee() != null ? task.getAssignee().getId() : null))
                    .filter(task -> TaskStatus.DONE.equals(task.getStatus()))
                    .toList();
            taskCountByStudent.put(studentId, completedTasks.size());

            Map<UUID, Double> taskScoreBySprint = new HashMap<>();
            for (Task task : completedTasks) {
                double taskWeight = task.getStoryPoint() != null ? task.getStoryPoint().doubleValue() : 1.0;
                UUID sprintId = task.getSprint() == null ? null : task.getSprint().getId();
                if (sprintId == null) {
                    continue;
                }
                Optional<ContributionCriterion> criterion = TaskContributionClassifier.classify(task);
                if (criterion.isPresent()) {
                    int attachments = task.getId() == null
                            ? 0
                            : attachmentCountByTaskId.getOrDefault(task.getId(), 0);
                    if (TaskContributionClassifier.storyPointsRecognized(criterion.get(), attachments)) {
                        switch (criterion.get()) {
                            case CODE -> codeScoreByStudent.merge(studentId, taskWeight, Double::sum);
                            case TEST -> testScoreByStudent.merge(studentId, taskWeight, Double::sum);
                            case DOCUMENT -> documentScoreByStudent.merge(studentId, taskWeight, Double::sum);
                            case RESEARCH -> researchScoreByStudent.merge(studentId, taskWeight, Double::sum);
                        }
                        SprintFirstContributionMixer.addRecognized(
                                recognizedByStudentThenSprint,
                                studentId,
                                sprintId,
                                criterion.get(),
                                taskWeight
                        );
                    }
                }
                taskScoreBySprint.merge(sprintId, taskWeight, Double::sum);
            }
            taskScoreByStudentSprint.put(studentId, taskScoreBySprint);

            Map<UUID, List<PeerReview>> reviewsBySprint = peerReviewsByStudentAndSprint
                    .getOrDefault(studentId, Map.of());
            List<PeerReview> studentPeerReviews = reviewsBySprint.values().stream()
                    .flatMap(List::stream)
                    .toList();
            peerReviewCountByStudent.put(studentId, studentPeerReviews.size());
        }

        double totalCode = codeScoreByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalTest = testScoreByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalDocument = documentScoreByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalResearch = researchScoreByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        // CODE/TEST/DOCUMENT/RESEARCH buckets are filled only by DONE Tasks whose labels contain
        // exactly one reserved marker. DOCUMENT/RESEARCH story points count only when that Task
        // has at least one Jira attachment. Unlabeled or conflicting-marker Tasks do not enter
        // any criterion. Tasks with no sprint are ignored. Per sprint: slice = Σ SP_criterion ×
        // weight (no share mix, no redistributing unused weights). Sprint % = (slice × P_s) /
        // team adjust. Project final = (Σ slice × project P) / team adjust. Radar slice % is
        // project-level share only.
        ContributionSliceWeights configuredWeights = sliceWeightResolver.resolve(team);
        List<UUID> scoringSprintIds = new ArrayList<>();
        for (Sprint sprint : sprints) {
            if (sprint != null && sprint.getId() != null) {
                scoringSprintIds.add(sprint.getId());
            }
        }
        SprintFirstContributionMixer.Result sprintMix = SprintFirstContributionMixer.mix(
                memberStudentIds,
                scoringSprintIds,
                recognizedByStudentThenSprint,
                peerScoreBySprint,
                configuredWeights
        );

        for (TeamMember member : members) {
            Student student = member.getStudent();
            if (student == null || student.getId() == null) {
                continue;
            }
            UUID studentId = student.getId();
            Map<UUID, List<PeerReview>> reviewsBySprint = peerReviewsByStudentAndSprint
                    .getOrDefault(studentId, Map.of());
            List<TeamContributionMemberResponse.SprintContributionBreakdown> sprintBreakdowns =
                    buildSprintBreakdowns(
                            sprints,
                            studentId,
                            taskScoreByStudentSprint.getOrDefault(studentId, Map.of()),
                            reviewsBySprint,
                            peerScoreBySprint,
                            recognizedByStudentThenSprint,
                            sprintMix
                    );
            double adjustedSprintScore = sprintBreakdowns.stream()
                    .mapToDouble(TeamContributionMemberResponse.SprintContributionBreakdown::adjustedTaskScore)
                    .sum();
            adjustedSprintScoreByStudent.put(studentId, adjustedSprintScore);
            sprintBreakdownsByStudent.put(studentId, sprintBreakdowns);
        }

        double totalAdjustedTaskScore = adjustedSprintScoreByStudent.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        Map<UUID, Double> codeContributionByStudent = new HashMap<>();
        Map<UUID, Double> testContributionByStudent = new HashMap<>();
        Map<UUID, Double> documentContributionByStudent = new HashMap<>();
        Map<UUID, Double> researchContributionByStudent = new HashMap<>();
        Map<UUID, Double> taskContributionByStudent = new HashMap<>();
        Map<UUID, Double> baseAdjustedContributionByStudent = new HashMap<>();
        for (UUID studentId : memberStudentIds) {
            double codeContribution = totalCode > 0 ? (codeScoreByStudent.getOrDefault(studentId, 0.0) / totalCode) * 100.0 : 0.0;
            double testContribution = totalTest > 0 ? (testScoreByStudent.getOrDefault(studentId, 0.0) / totalTest) * 100.0 : 0.0;
            double documentContribution = totalDocument > 0
                    ? (documentScoreByStudent.getOrDefault(studentId, 0.0) / totalDocument) * 100.0
                    : 0.0;
            double researchContribution = totalResearch > 0
                    ? (researchScoreByStudent.getOrDefault(studentId, 0.0) / totalResearch) * 100.0
                    : 0.0;
            double taskContribution = totalAdjustedTaskScore > 0.0
                    ? (adjustedSprintScoreByStudent.getOrDefault(studentId, 0.0) / totalAdjustedTaskScore) * 100.0
                    : 0.0;
            codeContributionByStudent.put(studentId, codeContribution);
            testContributionByStudent.put(studentId, testContribution);
            documentContributionByStudent.put(studentId, documentContribution);
            researchContributionByStudent.put(studentId, researchContribution);
            taskContributionByStudent.put(studentId, taskContribution);
            baseAdjustedContributionByStudent.put(
                    studentId,
                    sprintMix.finalPercentageByStudent().getOrDefault(studentId, 0.0)
            );
        }

        Map<UUID, Double> finalContributionByStudent = normalizeContributionsWithOverrides(
                baseAdjustedContributionByStudent,
                approvedOverrideByStudent,
                memberStudentIds
        );

        for (TeamMember member : members) {
            Student student = member.getStudent();
            if (student == null || student.getId() == null) {
                continue;
            }
            UUID studentId = student.getId();

            int evidenceCount = 0;
            if (commitCountByStudent.getOrDefault(studentId, 0) > 0) {
                evidenceCount++;
            }
            if (taskCountByStudent.getOrDefault(studentId, 0) > 0) {
                evidenceCount++;
            }
            if (documentScoreByStudent.getOrDefault(studentId, 0.0) > 0.0) {
                evidenceCount++;
            }
            if (peerReviewCountByStudent.getOrDefault(studentId, 0) > 0) {
                evidenceCount++;
            }

            List<ContributionWarning> warnings = buildWarnings(
                    studentId,
                    evidenceCount,
                    finalContributionByStudent.getOrDefault(studentId, 0.0),
                    peerCoefficientByStudent.getOrDefault(studentId, 1.0),
                    commitCountByStudent.getOrDefault(studentId, 0),
                    documentScoreByStudent.getOrDefault(studentId, 0.0).intValue(),
                    peerReviewCountByStudent.getOrDefault(studentId, 0)
            );

            result.add(new TeamContributionMemberResponse(
                    studentId,
                    student.getFullName(),
                    student.getStudentCode(),
                    codeContributionByStudent.getOrDefault(studentId, 0.0),
                    documentContributionByStudent.getOrDefault(studentId, 0.0),
                    codeContributionByStudent.getOrDefault(studentId, 0.0),
                    documentContributionByStudent.getOrDefault(studentId, 0.0),
                    testContributionByStudent.getOrDefault(studentId, 0.0),
                    testContributionByStudent.getOrDefault(studentId, 0.0),
                    researchContributionByStudent.getOrDefault(studentId, 0.0),
                    researchContributionByStudent.getOrDefault(studentId, 0.0),
                    peerCoefficientByStudent.getOrDefault(studentId, 1.0),
                    taskContributionByStudent.getOrDefault(studentId, 0.0),
                    taskContributionByStudent.getOrDefault(studentId, 0.0),
                    sprintMix.sliceScore(studentId),
                    sprintMix.slicePercentage(studentId),
                    finalContributionByStudent.getOrDefault(studentId, 0.0),
                    evidenceCount,
                    sprintBreakdownsByStudent.getOrDefault(studentId, List.of()),
                    warnings
            ));
        }

        return new TeamContributionEvaluationResponse(teamId, projectId, LocalDateTime.now(), result);
    }

    private void requireContributionReadAccess(SagaPrincipal principal, Team team) {
        if (principal == null || principal.localProfileId() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && team.getCourse() != null
                && team.getCourse().getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        team.getCourse().getInstructor().getId()
                )) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && teamMemberRepository.existsByTeamIdAndStudentIdAndRoleInTeam(
                        team.getId(),
                        principal.localProfileId(),
                        RoleInTeam.LEADER
                )) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this Team's contribution evaluation");
    }

    @Transactional
    public ContributionOverrideResponse requestContributionOverride(
            SagaPrincipal principal,
            UUID teamId,
            ContributionOverrideRequest request
    ) {
        if (request == null || request.studentId() == null || request.proposedPercentage() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student and proposed percentage are required");
        }
        if (request.proposedPercentage() < 0.0 || request.proposedPercentage() > 100.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposed percentage must be between 0 and 100");
        }
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        requireContributionOverrideAccess(principal, team);
        boolean isTeamMember = teamMemberRepository.findByTeamId(teamId).stream()
                .anyMatch(member -> member.getStudent() != null && member.getStudent().getId() != null
                        && request.studentId().equals(member.getStudent().getId()));
        if (!isTeamMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected student is not a member of this team");
        }

        UUID effectiveLecturerId = principal.applicationRole() == ApplicationRole.LECTURER
                ? principal.localProfileId()
                : request.lecturerId();
        Lecturer lecturer = effectiveLecturerId == null
                ? null
                : lecturerRepository.findById(effectiveLecturerId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));

        PolicyOverrideRequest overrideRequest = PolicyOverrideRequest.builder()
                .clazz(team.getCourse() != null ? team.getCourse().getClazz() : null)
                .lecturer(lecturer)
                .targetConfigId(request.studentId())
                .type(TEAM_CONTRIBUTION_OVERRIDE_TYPE)
                .proposedThreshold(request.proposedPercentage().floatValue())
                .reason(request.reason())
                .status(PolicyOverrideStatus.APPROVED)
                .resolvedAt(LocalDateTime.now())
                .build();

        PolicyOverrideRequest savedRequest = policyOverrideRequestRepository.save(overrideRequest);
        return new ContributionOverrideResponse(
                savedRequest.getId(),
                request.studentId(),
                request.proposedPercentage(),
                savedRequest.getStatus(),
                "Contribution override applied successfully"
        );
    }

    private Map<UUID, Double> loadApprovedOverrides(Team team, List<UUID> studentIds) {
        if (team.getCourse() == null || team.getCourse().getClazz() == null || studentIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, Double> overrides = new HashMap<>();
        List<PolicyOverrideRequest> requests = policyOverrideRequestRepository.findByTypeAndStatusAndClazz_Id(
                TEAM_CONTRIBUTION_OVERRIDE_TYPE,
                PolicyOverrideStatus.APPROVED,
                team.getCourse().getClazz().getId()
        );
        for (PolicyOverrideRequest request : requests) {
            if (request.getTargetConfigId() == null || !studentIds.contains(request.getTargetConfigId())) {
                continue;
            }
            if (request.getProposedThreshold() != null) {
                overrides.put(request.getTargetConfigId(), request.getProposedThreshold().doubleValue());
            }
        }
        return overrides;
    }

    private void requireContributionOverrideAccess(
            SagaPrincipal principal,
            Team team
    ) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() != ApplicationRole.LECTURER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only admin or lecturer can override contribution percentages"
            );
        }
        UUID principalLecturerId = principal.localProfileId();
        if (team.getCourse() == null
                || team.getCourse().getInstructor() == null
                || !Objects.equals(team.getCourse().getInstructor().getId(), principalLecturerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only override contributions for teams in your own courses"
            );
        }
    }

    private Map<UUID, Double> normalizeContributionsWithOverrides(
            Map<UUID, Double> baseAdjustedContributionByStudent,
            Map<UUID, Double> approvedOverrideByStudent,
            List<UUID> memberStudentIds
    ) {
        Map<UUID, Double> normalized = new LinkedHashMap<>();
        double totalOverride = approvedOverrideByStudent.values().stream().mapToDouble(Double::doubleValue).sum();
        List<UUID> remainingStudentIds = memberStudentIds.stream()
                .filter(studentId -> !approvedOverrideByStudent.containsKey(studentId))
                .toList();
        if (remainingStudentIds.isEmpty()) {
            if (totalOverride <= 0.0) {
                return splitBudgetEqually(memberStudentIds, 100.0);
            }
            for (UUID studentId : memberStudentIds) {
                normalized.put(
                        studentId,
                        (approvedOverrideByStudent.getOrDefault(studentId, 0.0) / totalOverride) * 100.0
                );
            }
            return normalized;
        }

        double totalBase = remainingStudentIds.stream()
                .mapToDouble(studentId -> baseAdjustedContributionByStudent.getOrDefault(studentId, 0.0))
                .sum();
        double normalizedOverrideTotal = totalOverride > 100.0 && totalOverride > 0.0
                ? 100.0
                : Math.max(totalOverride, 0.0);
        double remainingBudget = Math.max(100.0 - normalizedOverrideTotal, 0.0);

        for (UUID studentId : memberStudentIds) {
            if (approvedOverrideByStudent.containsKey(studentId)) {
                double overrideValue = approvedOverrideByStudent.get(studentId);
                double normalizedOverrideValue = totalOverride > 100.0
                        ? (overrideValue / totalOverride) * 100.0
                        : overrideValue;
                normalized.put(studentId, normalizedOverrideValue);
                continue;
            }
            double baseContribution = baseAdjustedContributionByStudent.getOrDefault(studentId, 0.0);
            double normalizedContribution = totalBase > 0.0 && remainingBudget > 0.0
                    ? (baseContribution / totalBase) * remainingBudget
                    : (remainingStudentIds.isEmpty() || remainingBudget <= 0.0
                    ? 0.0
                    : remainingBudget / remainingStudentIds.size());
            normalized.put(studentId, normalizedContribution);
        }
        return normalized;
    }

    private Map<UUID, Double> splitBudgetEqually(List<UUID> studentIds, double budget) {
        Map<UUID, Double> equallySplit = new LinkedHashMap<>();
        if (studentIds.isEmpty()) {
            return equallySplit;
        }
        double perStudent = budget / studentIds.size();
        for (UUID studentId : studentIds) {
            equallySplit.put(studentId, perStudent);
        }
        return equallySplit;
    }

    private Map<UUID, Integer> evidenceCounts(UUID projectId) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (TaskAttachment attachment : taskAttachmentRepository.findByTask_Project_Id(projectId)) {
            if (attachment.getTask() == null || attachment.getTask().getId() == null) {
                continue;
            }
            counts.merge(attachment.getTask().getId(), 1, Integer::sum);
        }
        for (TaskWebLink webLink : taskWebLinkRepository.findByTask_Project_Id(projectId)) {
            if (webLink.getTask() == null || webLink.getTask().getId() == null) {
                continue;
            }
            counts.merge(webLink.getTask().getId(), 1, Integer::sum);
        }
        return counts;
    }

    private List<ContributionWarning> buildWarnings(
            UUID studentId,
            int evidenceCount,
            double finalContributionPercentage,
            double peerCoefficient,
            int commitCount,
            int documentCount,
            int peerReviewCount
    ) {
        List<ContributionWarning> warnings = new ArrayList<>();
        if (peerReviewCount == 0 && finalContributionPercentage >= 50.0) {
            warnings.add(new ContributionWarning(
                    "NO_PEER_REVIEW",
                    "No peer review evidence is available for this student yet.",
                    "WARNING"
            ));
        }
        if (peerCoefficient <= 0.6 && finalContributionPercentage >= 40.0) {
            warnings.add(new ContributionWarning(
                    "LOW_PEER_REVIEW",
                    "Peer review coefficient is low and should be reviewed manually.",
                    "WARNING"
            ));
        }
        if (evidenceCount <= 1 && finalContributionPercentage >= 60.0) {
            warnings.add(new ContributionWarning(
                    "INSUFFICIENT_EVIDENCE",
                    "Contribution is high but backed by limited supporting evidence.",
                    "WARNING"
            ));
        }
        if (commitCount == 0 && documentCount == 0 && peerReviewCount == 0) {
            warnings.add(new ContributionWarning(
                    "NO_EVIDENCE",
                    "No code or document evidence was found for this student.",
                    "WARNING"
            ));
        }
        return warnings;
    }

    private Map<UUID, Map<UUID, List<PeerReview>>> groupPeerReviews(
            List<PeerReview> peerReviews
    ) {
        Map<UUID, Map<UUID, List<PeerReview>>> grouped = new HashMap<>();
        for (PeerReview review : peerReviews) {
            if (review.getReviewee() == null || review.getReviewee().getId() == null
                    || review.getSprint() == null || review.getSprint().getId() == null) {
                continue;
            }
            grouped.computeIfAbsent(review.getReviewee().getId(), ignored -> new HashMap<>())
                    .computeIfAbsent(review.getSprint().getId(), ignored -> new ArrayList<>())
                    .add(review);
        }
        return grouped;
    }

    private List<Sprint> resolveSprints(
            UUID projectId,
            List<Task> tasks,
            List<PeerReview> peerReviews
    ) {
        Map<UUID, Sprint> orderedSprints = new LinkedHashMap<>();
        for (Sprint sprint : sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)) {
            orderedSprints.put(sprint.getId(), sprint);
        }
        for (Task task : tasks) {
            if (task.getSprint() != null && task.getSprint().getId() != null) {
                orderedSprints.putIfAbsent(task.getSprint().getId(), task.getSprint());
            }
        }
        for (PeerReview review : peerReviews) {
            if (review.getSprint() != null && review.getSprint().getId() != null) {
                orderedSprints.putIfAbsent(review.getSprint().getId(), review.getSprint());
            }
        }
        return new ArrayList<>(orderedSprints.values());
    }

    private Map<UUID, Double> totalPeerScoreByStudent(List<PeerReview> reviews) {
        Map<UUID, Double> totals = new LinkedHashMap<>();
        for (PeerReview review : reviews) {
            if (review.getReviewee() == null || review.getReviewee().getId() == null) {
                continue;
            }
            totals.merge(review.getReviewee().getId(), peerValue(review), Double::sum);
        }
        return totals;
    }

    private List<TeamContributionMemberResponse.SprintContributionBreakdown>
            buildSprintBreakdowns(
                    List<Sprint> sprints,
                    UUID studentId,
                    Map<UUID, Double> taskScoreBySprint,
                    Map<UUID, List<PeerReview>> reviewsBySprint,
                    Map<UUID, Map<UUID, Double>> peerScoreBySprint,
                    Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
                    SprintFirstContributionMixer.Result sprintMix
            ) {
        List<TeamContributionMemberResponse.SprintContributionBreakdown> breakdowns =
                new ArrayList<>();
        for (Sprint sprint : sprints) {
            if (sprint == null || sprint.getId() == null) {
                continue;
            }
            List<PeerReview> sprintReviews = reviewsBySprint.getOrDefault(
                    sprint.getId(),
                    List.of()
            );
            double taskScore = taskScoreBySprint.getOrDefault(sprint.getId(), 0.0);
            double retrospectiveMultiplier = resolveRetrospectiveMultiplier(
                    sprint.getId(),
                    sprintReviews,
                    peerScoreBySprint
            );
            if (taskScore == 0.0 && sprintReviews.isEmpty()) {
                continue;
            }
            double[] recognized = SprintFirstContributionMixer.copyRecognizedBucket(
                    recognizedByStudentThenSprint,
                    studentId,
                    sprint.getId()
            );
            breakdowns.add(new TeamContributionMemberResponse.SprintContributionBreakdown(
                    sprint.getId(),
                    sprint.getName(),
                    taskScore,
                    retrospectiveMultiplier,
                    taskScore * retrospectiveMultiplier,
                    sprintReviews.size(),
                    sprintMix.sliceScoreInSprint(sprint.getId(), studentId),
                    sprintMix.slicePercentageInSprint(sprint.getId(), studentId),
                    sprintMix.percentageInSprint(sprint.getId(), studentId),
                    recognized[0],
                    recognized[1],
                    recognized[2],
                    recognized[3]
            ));
        }
        return breakdowns;
    }

    private double resolveRetrospectiveMultiplier(
            UUID sprintId,
            List<PeerReview> reviews,
            Map<UUID, Map<UUID, Double>> peerScoreBySprint
    ) {
        if (sprintId == null) {
            return 0.0;
        }
        Map<UUID, Double> sprintScores = peerScoreBySprint.getOrDefault(sprintId, Map.of());
        double totalScore = sprintScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalScore <= 0.0) {
            return 1.0;
        }
        double studentScore = reviews.stream()
                .filter(review -> review.getStarRating() != null)
                .mapToDouble(review -> review.getStarRating().doubleValue())
                .sum();
        return studentScore / totalScore;
    }

    private Map<UUID, Map<UUID, Double>> totalPeerScoreBySprint(List<PeerReview> reviews) {
        Map<UUID, Map<UUID, Double>> totals = new LinkedHashMap<>();
        for (PeerReview review : reviews) {
            if (review.getSprint() == null || review.getSprint().getId() == null) {
                continue;
            }
            if (review.getReviewee() == null || review.getReviewee().getId() == null) {
                continue;
            }
            totals.computeIfAbsent(review.getSprint().getId(), ignored -> new LinkedHashMap<>())
                    .merge(review.getReviewee().getId(), peerValue(review), Double::sum);
        }
        return totals;
    }

    private double peerValue(PeerReview review) {
        return review.getStarRating() == null ? 0.0 : review.getStarRating().doubleValue();
    }
}
