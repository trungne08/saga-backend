package com.saga.be.service.contribution;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.value.TaskComponentSnapshot;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// Calculates the shared local contribution snapshot used by service callers.
public class ContributionCalculationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CommitDataRepository commitRepository;
    private final DocumentRepository documentRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final PeerReviewRepository peerReviewRepository;

    public ContributionCalculationService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            CommitDataRepository commitRepository,
            DocumentRepository documentRepository,
            SprintRepository sprintRepository,
            TaskRepository taskRepository,
            PeerReviewRepository peerReviewRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.commitRepository = commitRepository;
        this.documentRepository = documentRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.peerReviewRepository = peerReviewRepository;
    }

    @Transactional(readOnly = true)
    public ProjectContributionCalculation calculate(
            UUID projectId,
            Map<UUID, BigDecimal> overrides
    ) {
        Team team = teamRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ContributionCalculationException(
                        "A Project contribution calculation requires a Team"
                ));
        List<Student> students = teamMemberRepository.findByTeamId(team.getId())
                .stream()
                .map(member -> member.getStudent())
                .distinct()
                .toList();
        List<Sprint> sprints = sprintRepository.findByBoardProjectId(projectId);
        List<PeerReview> peerReviews = students.isEmpty()
                ? List.of()
                : peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                        students.stream().map(Student::getId).toList(),
                        projectId
                );
        Map<UUID, BigDecimal> normalizedOverrides = normalizedOverrides(overrides, students);
        Map<UUID, BigDecimal> peerScoreByStudent = totalPeerScoreByStudent(peerReviews);
        Map<UUID, Map<UUID, BigDecimal>> peerScoreBySprint = totalPeerScoreBySprint(peerReviews);
        BigDecimal totalPeerScore = peerScoreByStudent.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, Scores> scores = new LinkedHashMap<>();
        for (Student student : students) {
            scores.put(student.getId(), scores(
                    projectId,
                    student,
                    sprints,
                    peerScoreByStudent,
                    peerScoreBySprint,
                    totalPeerScore
            ));
        }

        BigDecimal totalCode = total(scores.values(), Scores::code);
        BigDecimal totalDocument = total(scores.values(), Scores::document);
        BigDecimal totalDesign = total(scores.values(), Scores::design);
        BigDecimal totalTask = total(scores.values(), Scores::adjustedSprint);
        ContributionSliceWeights sliceWeights = ContributionSliceWeights.fromCourse(team.getCourse())
                .normalizeForActiveSlices(
                        totalCode.signum() > 0,
                        totalDocument.signum() > 0,
                        totalDesign.signum() > 0
                );
        Map<UUID, BigDecimal> adjusted = new LinkedHashMap<>();
        for (Map.Entry<UUID, Scores> entry : scores.entrySet()) {
            Scores value = entry.getValue();
            BigDecimal raw = weightedRawContribution(
                    value,
                    totalCode,
                    totalDocument,
                    totalDesign,
                    sliceWeights
            );
            adjusted.put(entry.getKey(), raw.multiply(value.peerCoefficient()));
        }
        Map<UUID, BigDecimal> finalContributions = finalContributions(
                students,
                normalizedOverrides,
                adjusted
        );
        List<ContributionBreakdown> breakdowns = new ArrayList<>();
        for (Student student : students) {
            Scores value = scores.get(student.getId());
            BigDecimal raw = weightedRawContribution(
                    value,
                    totalCode,
                    totalDocument,
                    totalDesign,
                    sliceWeights
            );
            breakdowns.add(new ContributionBreakdown(
                    student.getId(), value.code(), value.document(), value.design(),
                    value.adjustedSprint(), value.peerCoefficient(),
                    percent(value.code(), totalCode), percent(value.document(), totalDocument),
                    percent(value.design(), totalDesign), percent(value.adjustedSprint(), totalTask),
                    raw, adjusted.get(student.getId()), finalContributions.get(student.getId())
            ));
        }
        return new ProjectContributionCalculation(projectId, breakdowns);
    }

    private Scores scores(
            UUID projectId,
            Student student,
            List<Sprint> sprints,
            Map<UUID, BigDecimal> peerScoreByStudent,
            Map<UUID, Map<UUID, BigDecimal>> peerScoreBySprint,
            BigDecimal totalPeerScore
    ) {
        BigDecimal code = BigDecimal.ZERO;
        BigDecimal document = BigDecimal.valueOf(documentRepository.countByProjectIdAndAuthorIdAndTypeNot(projectId, student.getId(), DocumentType.DESIGN));
        BigDecimal design = BigDecimal.valueOf(documentRepository.countByProjectIdAndAuthorIdAndType(projectId, student.getId(), DocumentType.DESIGN));
        BigDecimal adjustedSprint = BigDecimal.ZERO;
        for (CommitData commit : commitRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull(student.getId(), projectId)) {
            if (commit.getTask() == null) {
                continue;
            }
            BigDecimal commitWeight = taskPoints(commit.getTask());
            switch (classifyTaskSlice(commit.getTask())) {
                case CODE -> code = code.add(commitWeight);
                case DOCUMENT -> document = document.add(commitWeight);
                case DESIGN -> design = design.add(commitWeight);
            }
        }
        List<Task> tasks = taskRepository.findByProjectIdAndAssigneeId(projectId, student.getId());
        if (tasks != null && !tasks.isEmpty()) {
            for (Task task : tasks) {
                if (task.getStatus() != TaskStatus.DONE) {
                    continue;
                }
                BigDecimal taskPoints = taskPoints(task);
                BigDecimal multiplier = retrospectiveMultiplier(
                        student.getId(),
                        task.getSprint() != null ? task.getSprint().getId() : null,
                        peerScoreByStudent,
                        peerScoreBySprint
                );
                BigDecimal weightedTaskPoints = taskPoints.multiply(multiplier);
                adjustedSprint = adjustedSprint.add(weightedTaskPoints);
                switch (classifyTaskSlice(task)) {
                    case CODE -> code = code.add(weightedTaskPoints);
                    case DOCUMENT -> document = document.add(weightedTaskPoints);
                    case DESIGN -> design = design.add(weightedTaskPoints);
                }
            }
        } else {
            for (Sprint sprint : sprints) {
                BigDecimal task = BigDecimal.valueOf(zero(taskRepository.sumDoneEffectiveStoryPoints(projectId, sprint.getId(), student.getId())));
                adjustedSprint = adjustedSprint.add(task.multiply(retrospectiveMultiplier(
                        student.getId(),
                        sprint.getId(),
                        peerScoreByStudent,
                        peerScoreBySprint
                )));
            }
        }
        return new Scores(code, document, design, adjustedSprint, peerCoefficient(
                student.getId(),
                peerScoreByStudent,
                totalPeerScore
        ));
    }

    private BigDecimal retrospectiveMultiplier(
            UUID studentId,
            UUID sprintId,
            Map<UUID, BigDecimal> peerScoreByStudent,
            Map<UUID, Map<UUID, BigDecimal>> peerScoreBySprint
    ) {
        if (sprintId == null) {
            return BigDecimal.ONE;
        }
        Map<UUID, BigDecimal> sprintScores = peerScoreBySprint.getOrDefault(sprintId, Map.of());
        BigDecimal studentScore = sprintScores.getOrDefault(studentId, BigDecimal.ZERO);
        BigDecimal totalScore = sprintScores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return ratio(studentScore, totalScore);
    }

    private BigDecimal peerCoefficient(
            UUID studentId,
            Map<UUID, BigDecimal> peerScoreByStudent,
            BigDecimal totalPeerScore
    ) {
        return ratio(peerScoreByStudent.getOrDefault(studentId, BigDecimal.ZERO), totalPeerScore);
    }

    private Map<UUID, BigDecimal> totalPeerScoreByStudent(List<PeerReview> reviews) {
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        for (PeerReview review : reviews) {
            if (review.getReviewee() == null || review.getReviewee().getId() == null) {
                continue;
            }
            totals.merge(review.getReviewee().getId(), peerValue(review), BigDecimal::add);
        }
        return totals;
    }

    private Map<UUID, Map<UUID, BigDecimal>> totalPeerScoreBySprint(List<PeerReview> reviews) {
        Map<UUID, Map<UUID, BigDecimal>> totals = new LinkedHashMap<>();
        for (PeerReview review : reviews) {
            if (review.getSprint() == null || review.getSprint().getId() == null) {
                continue;
            }
            if (review.getReviewee() == null || review.getReviewee().getId() == null) {
                continue;
            }
            totals.computeIfAbsent(review.getSprint().getId(), ignored -> new LinkedHashMap<>())
                    .merge(review.getReviewee().getId(), peerValue(review), BigDecimal::add);
        }
        return totals;
    }

    private BigDecimal peerValue(PeerReview review) {
        return BigDecimal.valueOf(review.getStarRating() == null ? 0 : review.getStarRating());
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ONE;
        }
        return numerator.divide(denominator, MathContext.DECIMAL64);
    }

    private Map<UUID, BigDecimal> normalizedOverrides(Map<UUID, BigDecimal> overrides, List<Student> students) {
        Map<UUID, BigDecimal> safe = new LinkedHashMap<>();
        if (overrides == null) {
            return safe;
        }
        for (Map.Entry<UUID, BigDecimal> entry : overrides.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() < 0 || entry.getValue().compareTo(ONE_HUNDRED) > 0
                    || students.stream().noneMatch(student -> student.getId().equals(entry.getKey()))) {
                throw new ContributionCalculationException("Contribution override policy is unresolved");
            }
            safe.put(entry.getKey(), entry.getValue());
        }
        return safe;
    }

    private Map<UUID, BigDecimal> finalContributions(List<Student> students, Map<UUID, BigDecimal> overrides, Map<UUID, BigDecimal> adjusted) {
        BigDecimal overrideTotal = overrides.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        boolean allOverridden = overrides.size() == students.size();
        if (allOverridden) {
            if (overrideTotal.signum() == 0) {
                return splitBudgetEqually(students, ONE_HUNDRED);
            }
            for (Student student : students) {
                result.put(
                        student.getId(),
                        overrides.get(student.getId()).divide(overrideTotal, MathContext.DECIMAL64).multiply(ONE_HUNDRED)
                );
            }
            return result;
        }
        for (Student student : students) {
            if (overrides.containsKey(student.getId())) {
                result.put(student.getId(), overrideTotal.compareTo(ONE_HUNDRED) > 0
                        ? overrides.get(student.getId()).divide(overrideTotal, MathContext.DECIMAL64).multiply(ONE_HUNDRED)
                        : overrides.get(student.getId()));
            }
        }
        List<Student> remainingStudents = students.stream().filter(student -> !overrides.containsKey(student.getId())).toList();
        if (remainingStudents.isEmpty()) {
            return result;
        }
        BigDecimal remainingBudget = ONE_HUNDRED.subtract(result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalBase = remainingStudents.stream().map(student -> adjusted.get(student.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalBase.signum() == 0) {
            result.putAll(splitBudgetEqually(remainingStudents, remainingBudget));
            return result;
        }
        for (Student student : remainingStudents) {
            result.put(student.getId(), adjusted.get(student.getId()).divide(totalBase, MathContext.DECIMAL64).multiply(remainingBudget));
        }
        return result;
    }

    private BigDecimal taskPoints(Task task) {
        return BigDecimal.valueOf(task.getStoryPoint() == null ? 1 : task.getStoryPoint());
    }

    private ContributionSlice classifyTaskSlice(Task task) {
        String labelText = task.getLabels() == null
                ? ""
                : String.join(" ", task.getLabels());
        String componentText = task.getComponents() == null
                ? ""
                : task.getComponents().stream()
                        .map(TaskComponentSnapshot::name)
                        .filter(name -> name != null && !name.isBlank())
                        .reduce((left, right) -> left + " " + right)
                        .orElse("");
        String labelAndComponentText = String.join(" ", labelText, componentText).toLowerCase(Locale.ROOT);
        if (containsAny(labelAndComponentText, List.of("figma", "mockup", "prototype", "wireframe", "design system", "visual design", "ui/ux", "design review", "design"))) {
            return ContributionSlice.DESIGN;
        }
        if (containsAny(labelAndComponentText, List.of("doc", "document", "documentation", "wiki", "readme", "spec", "manual", "guide", "requirement"))) {
            return ContributionSlice.DOCUMENT;
        }
        if (task.getType() != null) {
            return switch (task.getType()) {
                case BUG, FEATURE, STORY, TASK, EPIC, SUBTASK -> ContributionSlice.CODE;
            };
        }
        String fallbackText = String.join(" ", task.getTitle() == null ? "" : task.getTitle(), task.getDescription() == null ? "" : task.getDescription()).toLowerCase(Locale.ROOT);
        if (containsAny(fallbackText, List.of("figma", "mockup", "prototype", "wireframe", "design system", "visual design", "ui/ux", "design review", "design"))) {
            return ContributionSlice.DESIGN;
        }
        if (containsAny(fallbackText, List.of("doc", "document", "documentation", "wiki", "readme", "spec", "manual", "guide", "requirement"))) {
            return ContributionSlice.DOCUMENT;
        }
        return ContributionSlice.CODE;
    }

    private boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(haystack::contains);
    }

    private BigDecimal percent(BigDecimal score, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : score.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED);
    }

    private BigDecimal total(Collection<Scores> scores, java.util.function.Function<Scores, BigDecimal> selector) {
        return scores.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal weightedRawContribution(
            Scores value,
            BigDecimal totalCode,
            BigDecimal totalDocument,
            BigDecimal totalDesign,
            ContributionSliceWeights sliceWeights
    ) {
        return percent(value.code(), totalCode).multiply(sliceWeights.codeRatio())
                .add(percent(value.document(), totalDocument).multiply(sliceWeights.documentRatio()))
                .add(percent(value.design(), totalDesign).multiply(sliceWeights.designRatio()));
    }

    private Map<UUID, BigDecimal> splitBudgetEqually(List<Student> students, BigDecimal budget) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        if (students.isEmpty()) {
            return result;
        }
        BigDecimal perStudent = budget.divide(BigDecimal.valueOf(students.size()), MathContext.DECIMAL64);
        for (Student student : students) {
            result.put(student.getId(), perStudent);
        }
        return result;
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private enum ContributionSlice {
        CODE,
        DOCUMENT,
        DESIGN
    }
 
    private record Scores(BigDecimal code, BigDecimal document, BigDecimal design, BigDecimal adjustedSprint, BigDecimal peerCoefficient) { }
}
