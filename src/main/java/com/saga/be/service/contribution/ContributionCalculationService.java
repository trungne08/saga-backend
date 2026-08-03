package com.saga.be.service.contribution;

import com.saga.be.entity.PeerReview;
import com.saga.be.entity.PeerReviewConfig;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.DocumentType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.PeerReviewConfigRepository;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContributionCalculationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal CODE_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal TASK_WEIGHT = new BigDecimal("0.6");

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CommitDataRepository commitRepository;
    private final DocumentRepository documentRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final PeerReviewConfigRepository peerReviewConfigRepository;

    public ContributionCalculationService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            CommitDataRepository commitRepository,
            DocumentRepository documentRepository,
            SprintRepository sprintRepository,
            TaskRepository taskRepository,
            PeerReviewRepository peerReviewRepository,
            PeerReviewConfigRepository peerReviewConfigRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.commitRepository = commitRepository;
        this.documentRepository = documentRepository;
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.peerReviewRepository = peerReviewRepository;
        this.peerReviewConfigRepository = peerReviewConfigRepository;
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
        UUID subjectId = team.getCourse().getSubject().getId();
        List<Student> students = teamMemberRepository.findByTeamId(team.getId())
                .stream()
                .map(member -> member.getStudent())
                .distinct()
                .toList();
        List<Sprint> sprints = sprintRepository.findByBoardProjectId(projectId);
        Map<UUID, BigDecimal> normalizedOverrides = normalizedOverrides(overrides, students);
        Map<UUID, Scores> scores = new LinkedHashMap<>();
        for (Student student : students) {
            scores.put(student.getId(), scores(projectId, subjectId, student, sprints));
        }

        BigDecimal totalCode = total(scores.values(), Scores::code);
        BigDecimal totalDocument = total(scores.values(), Scores::document);
        BigDecimal totalDesign = total(scores.values(), Scores::design);
        BigDecimal totalTask = total(scores.values(), Scores::adjustedSprint);
        Map<UUID, BigDecimal> adjusted = new LinkedHashMap<>();
        for (Map.Entry<UUID, Scores> entry : scores.entrySet()) {
            Scores value = entry.getValue();
            BigDecimal raw = percent(value.code(), totalCode).multiply(CODE_WEIGHT)
                    .add(percent(value.adjustedSprint(), totalTask).multiply(TASK_WEIGHT));
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
            BigDecimal raw = percent(value.code(), totalCode).multiply(CODE_WEIGHT)
                    .add(percent(value.adjustedSprint(), totalTask).multiply(TASK_WEIGHT));
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

    private Scores scores(UUID projectId, UUID subjectId, Student student, List<Sprint> sprints) {
        BigDecimal code = BigDecimal.valueOf(commitRepository.countByProjectIdAndAuthorId(projectId, student.getId()));
        BigDecimal document = BigDecimal.valueOf(documentRepository.countByProjectIdAndAuthorIdAndTypeNot(projectId, student.getId(), DocumentType.DESIGN));
        BigDecimal design = BigDecimal.valueOf(documentRepository.countByProjectIdAndAuthorIdAndType(projectId, student.getId(), DocumentType.DESIGN));
        BigDecimal adjustedSprint = BigDecimal.ZERO;
        for (Sprint sprint : sprints) {
            BigDecimal task = BigDecimal.valueOf(zero(taskRepository.sumDoneEffectiveStoryPoints(projectId, sprint.getId(), student.getId())));
            adjustedSprint = adjustedSprint.add(task.multiply(retrospectiveMultiplier(subjectId, student.getId(), sprint.getId())));
        }
        return new Scores(code, document, design, adjustedSprint, peerCoefficient(subjectId, student.getId(), projectId));
    }

    private BigDecimal retrospectiveMultiplier(UUID subjectId, UUID studentId, UUID sprintId) {
        return averageMultipliers(subjectId, peerReviewRepository.findByRevieweeIdAndSprintId(studentId, sprintId));
    }

    private BigDecimal peerCoefficient(UUID subjectId, UUID studentId, UUID projectId) {
        return averageMultipliers(subjectId, peerReviewRepository.findByRevieweeIdAndSprintBoardProjectId(studentId, projectId));
    }

    private BigDecimal averageMultipliers(UUID subjectId, List<PeerReview> reviews) {
        if (reviews.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PeerReview review : reviews) {
            sum = sum.add(multiplier(subjectId, review.getStarRating()));
        }
        return sum.divide(BigDecimal.valueOf(reviews.size()), MathContext.DECIMAL64);
    }

    private BigDecimal multiplier(UUID subjectId, Integer starRating) {
        List<PeerReviewConfig> configs = peerReviewConfigRepository
                .findApplicableBySubjectIdAndStarRating(subjectId, starRating);
        if (configs.size() != 1 || configs.get(0).getMultiplier() == null) {
            throw new ContributionCalculationException(
                    "Peer-review config precedence or multiplier is unresolved"
            );
        }
        return new BigDecimal(Float.toString(configs.get(0).getMultiplier()));
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
        if (allOverridden && overrideTotal.compareTo(ONE_HUNDRED) < 0) {
            throw new ContributionCalculationException("All-overridden contribution remainder policy is unresolved");
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
            if (remainingBudget.signum() > 0) {
                throw new ContributionCalculationException("Contribution total-base-zero policy is unresolved");
            }
            for (Student student : remainingStudents) {
                result.put(student.getId(), BigDecimal.ZERO);
            }
            return result;
        }
        for (Student student : remainingStudents) {
            result.put(student.getId(), adjusted.get(student.getId()).divide(totalBase, MathContext.DECIMAL64).multiply(remainingBudget));
        }
        return result;
    }

    private BigDecimal percent(BigDecimal score, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : score.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED);
    }

    private BigDecimal total(Collection<Scores> scores, java.util.function.Function<Scores, BigDecimal> selector) {
        return scores.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private record Scores(BigDecimal code, BigDecimal document, BigDecimal design, BigDecimal adjustedSprint, BigDecimal peerCoefficient) { }
}
