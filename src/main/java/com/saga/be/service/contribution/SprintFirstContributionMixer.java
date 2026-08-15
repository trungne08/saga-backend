package com.saga.be.service.contribution;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Absolute weighted slice per sprint, then project-level % from summed slices × project peer.
 * See {@code docs/CONTRIBUTION_CALCULATION_SPEC.md}.
 */
public final class SprintFirstContributionMixer {

    private SprintFirstContributionMixer() {
    }

    public record Result(
            Map<UUID, Double> sliceScoreByStudent,
            Map<UUID, Double> slicePercentageByStudent,
            Map<UUID, Double> finalPercentageByStudent,
            Map<UUID, Map<UUID, Double>> sliceScoreBySprintThenStudent,
            Map<UUID, Map<UUID, Double>> slicePercentageBySprintThenStudent,
            Map<UUID, Map<UUID, Double>> percentageBySprintThenStudent
    ) {
        public double sliceScore(UUID studentId) {
            return sliceScoreByStudent.getOrDefault(studentId, 0.0);
        }

        public double slicePercentage(UUID studentId) {
            return slicePercentageByStudent.getOrDefault(studentId, 0.0);
        }

        public double sliceScoreInSprint(UUID sprintId, UUID studentId) {
            return sliceScoreBySprintThenStudent
                    .getOrDefault(sprintId, Map.of())
                    .getOrDefault(studentId, 0.0);
        }

        public double slicePercentageInSprint(UUID sprintId, UUID studentId) {
            return slicePercentageBySprintThenStudent
                    .getOrDefault(sprintId, Map.of())
                    .getOrDefault(studentId, 0.0);
        }

        public double percentageInSprint(UUID sprintId, UUID studentId) {
            return percentageBySprintThenStudent
                    .getOrDefault(sprintId, Map.of())
                    .getOrDefault(studentId, 0.0);
        }
    }

    public static void addRecognized(
            Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
            UUID studentId,
            UUID sprintId,
            ContributionCriterion criterion,
            double taskWeight
    ) {
        double[] bucket = recognizedByStudentThenSprint
                .computeIfAbsent(studentId, ignored -> new HashMap<>())
                .computeIfAbsent(sprintId, ignored -> new double[4]);
        switch (criterion) {
            case CODE -> bucket[0] += taskWeight;
            case TEST -> bucket[1] += taskWeight;
            case DOCUMENT -> bucket[2] += taskWeight;
            case RESEARCH -> bucket[3] += taskWeight;
        }
    }

    public static Result mix(
            List<UUID> studentIds,
            List<UUID> scoringSprintIds,
            Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
            Map<UUID, Map<UUID, Double>> peerStarsBySprintThenStudent,
            ContributionSliceWeights configuredWeights
    ) {
        Map<UUID, Double> sliceSum = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            sliceSum.put(studentId, 0.0);
        }
        Map<UUID, Map<UUID, Double>> sliceBySprint = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Double>> slicePctBySprint = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Double>> bySprint = new LinkedHashMap<>();
        for (UUID sprintId : scoringSprintIds) {
            SprintMix sprintMix = scoreSprint(
                    studentIds,
                    sprintId,
                    recognizedByStudentThenSprint,
                    peerStarsBySprintThenStudent,
                    configuredWeights
            );
            if (sprintMix == null) {
                continue;
            }
            sliceBySprint.put(sprintId, sprintMix.sliceScores());
            slicePctBySprint.put(sprintId, sprintMix.slicePercentages());
            bySprint.put(sprintId, sprintMix.contributionPercentages());
            for (UUID studentId : studentIds) {
                sliceSum.merge(
                        studentId,
                        sprintMix.sliceScores().getOrDefault(studentId, 0.0),
                        Double::sum
                );
            }
        }
        double totalSlice = sliceSum.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> slicePercentages = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            slicePercentages.put(
                    studentId,
                    totalSlice > 0.0 ? sliceSum.get(studentId) / totalSlice * 100.0 : 0.0
            );
        }
        Map<UUID, Double> projectStars = projectStars(peerStarsBySprintThenStudent);
        double totalProjectStars = projectStars.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> adjusted = new LinkedHashMap<>();
        double totalAdjusted = 0.0;
        for (UUID studentId : studentIds) {
            double peer = totalProjectStars > 0.0
                    ? projectStars.getOrDefault(studentId, 0.0) / totalProjectStars
                    : 1.0;
            double adj = sliceSum.getOrDefault(studentId, 0.0) * peer;
            adjusted.put(studentId, adj);
            totalAdjusted += adj;
        }
        Map<UUID, Double> finals = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            finals.put(
                    studentId,
                    totalAdjusted > 0.0 ? adjusted.get(studentId) / totalAdjusted * 100.0 : 0.0
            );
        }
        return new Result(
                sliceSum,
                slicePercentages,
                finals,
                sliceBySprint,
                slicePctBySprint,
                bySprint
        );
    }

    private static SprintMix scoreSprint(
            List<UUID> studentIds,
            UUID sprintId,
            Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
            Map<UUID, Map<UUID, Double>> peerStarsBySprintThenStudent,
            ContributionSliceWeights configuredWeights
    ) {
        if (sprintId == null) {
            return null;
        }
        Map<UUID, Double> slices = new LinkedHashMap<>();
        double totalSlice = 0.0;
        for (UUID studentId : studentIds) {
            double memberSlice = slice(
                    recognizedBucket(recognizedByStudentThenSprint, studentId, sprintId),
                    configuredWeights
            );
            slices.put(studentId, memberSlice);
            totalSlice += memberSlice;
        }
        if (totalSlice <= 0.0) {
            return null;
        }
        Map<UUID, Double> slicePercentages = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            slicePercentages.put(studentId, slices.get(studentId) / totalSlice * 100.0);
        }
        Map<UUID, Double> peerInSprint = peerStarsBySprintThenStudent.getOrDefault(sprintId, Map.of());
        double totalPeer = peerInSprint.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Double> adjusted = new LinkedHashMap<>();
        double totalAdjusted = 0.0;
        for (UUID studentId : studentIds) {
            double peer = totalPeer > 0.0
                    ? peerInSprint.getOrDefault(studentId, 0.0) / totalPeer
                    : 1.0;
            double adj = slices.get(studentId) * peer;
            adjusted.put(studentId, adj);
            totalAdjusted += adj;
        }
        Map<UUID, Double> pct = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            pct.put(
                    studentId,
                    totalAdjusted > 0.0 ? adjusted.get(studentId) / totalAdjusted * 100.0 : 0.0
            );
        }
        return new SprintMix(slices, slicePercentages, pct);
    }

    private static double slice(double[] bucket, ContributionSliceWeights weights) {
        return bucket[0] * weights.codeRatio().doubleValue()
                + bucket[1] * weights.testRatio().doubleValue()
                + bucket[2] * weights.documentRatio().doubleValue()
                + bucket[3] * weights.researchRatio().doubleValue();
    }

    private static Map<UUID, Double> projectStars(Map<UUID, Map<UUID, Double>> peerStarsBySprintThenStudent) {
        Map<UUID, Double> totals = new HashMap<>();
        for (Map<UUID, Double> byStudent : peerStarsBySprintThenStudent.values()) {
            for (Map.Entry<UUID, Double> entry : byStudent.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return totals;
    }

    public static double[] copyRecognizedBucket(
            Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
            UUID studentId,
            UUID sprintId
    ) {
        double[] source = recognizedBucket(recognizedByStudentThenSprint, studentId, sprintId);
        return new double[] {source[0], source[1], source[2], source[3]};
    }

    private static double[] recognizedBucket(
            Map<UUID, Map<UUID, double[]>> recognizedByStudentThenSprint,
            UUID studentId,
            UUID sprintId
    ) {
        Map<UUID, double[]> bySprint = recognizedByStudentThenSprint.get(studentId);
        if (bySprint == null) {
            return new double[4];
        }
        return bySprint.getOrDefault(sprintId, new double[4]);
    }

    private record SprintMix(
            Map<UUID, Double> sliceScores,
            Map<UUID, Double> slicePercentages,
            Map<UUID, Double> contributionPercentages
    ) {
    }
}
