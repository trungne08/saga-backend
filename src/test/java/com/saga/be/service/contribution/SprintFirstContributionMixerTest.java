package com.saga.be.service.contribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SprintFirstContributionMixerTest {

    private static final ContributionSliceWeights SPEC_WEIGHTS = ContributionSliceWeights.normalizeConfigured(
            BigDecimal.valueOf(40),
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(15),
            BigDecimal.valueOf(35)
    );

    @Test
    void sumsStoryPointsThenMultipliesCriterionWeightWithoutShareMix() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintId, ContributionCriterion.CODE, 3.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintId, ContributionCriterion.CODE, 2.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, bob, sprintId, ContributionCriterion.TEST, 5.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice, bob),
                List.of(sprintId),
                recognized,
                Map.of(),
                SPEC_WEIGHTS
        );

        // Alice slice = (3+2)×0.40 = 2.00; Bob slice = 5×0.10 = 0.50; P = 1.
        assertEquals(2.0, result.sliceScore(alice), 0.0001);
        assertEquals(0.5, result.sliceScore(bob), 0.0001);
        assertEquals(80.0, result.slicePercentage(alice), 0.0001);
        assertEquals(20.0, result.slicePercentage(bob), 0.0001);
        assertEquals(80.0, result.percentageInSprint(sprintId, alice), 0.0001);
        assertEquals(20.0, result.percentageInSprint(sprintId, bob), 0.0001);
        assertEquals(80.0, result.finalPercentageByStudent().get(alice), 0.0001);
        assertEquals(20.0, result.finalPercentageByStudent().get(bob), 0.0001);
    }

    @Test
    void doesNotRedistributeUnusedCriterionWeights() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintId, ContributionCriterion.CODE, 2.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, bob, sprintId, ContributionCriterion.DOCUMENT, 1.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice, bob),
                List.of(sprintId),
                recognized,
                Map.of(sprintId, Map.of(alice, 1.0, bob, 1.0)),
                ContributionSliceWeights.normalizeConfigured(
                        BigDecimal.valueOf(60),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(20)
                )
        );

        // Alice 2×0.60 = 1.20; Bob 1×0.20 = 0.20; equal P so slice % equals final %.
        assertEquals(1.20, result.sliceScore(alice), 0.0001);
        assertEquals(0.20, result.sliceScore(bob), 0.0001);
        assertEquals(85.7143, result.slicePercentage(alice), 0.001);
        assertEquals(14.2857, result.slicePercentage(bob), 0.001);
        assertEquals(85.7143, result.finalPercentageByStudent().get(alice), 0.001);
        assertEquals(14.2857, result.finalPercentageByStudent().get(bob), 0.001);
    }

    @Test
    void weightsSprintsBySliceVolumeInsteadOfEqualAveragingPercentages() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID sprintOne = UUID.randomUUID();
        UUID sprintTwo = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintOne, ContributionCriterion.CODE, 10.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintTwo, ContributionCriterion.RESEARCH, 1.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, bob, sprintTwo, ContributionCriterion.RESEARCH, 1.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice, bob),
                List.of(sprintOne, sprintTwo),
                recognized,
                Map.of(
                        sprintOne, Map.of(alice, 1.0, bob, 1.0),
                        sprintTwo, Map.of(alice, 1.0, bob, 1.0)
                ),
                SPEC_WEIGHTS
        );

        assertEquals(100.0, result.percentageInSprint(sprintOne, alice), 0.0001);
        assertEquals(0.0, result.percentageInSprint(sprintOne, bob), 0.0001);
        assertEquals(50.0, result.percentageInSprint(sprintTwo, alice), 0.0001);
        assertEquals(50.0, result.percentageInSprint(sprintTwo, bob), 0.0001);
        // Σslice Alice = 10×0.40 + 1×0.35 = 4.35; Bob = 0.35; P = 0.5 → 92.553 / 7.447.
        assertEquals(92.5532, result.finalPercentageByStudent().get(alice), 0.001);
        assertEquals(7.4468, result.finalPercentageByStudent().get(bob), 0.001);
    }

    @Test
    void appliesPeerInsideTheSprintBeforeNormalizingThatSprintToOneHundred() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintId, ContributionCriterion.CODE, 3.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, bob, sprintId, ContributionCriterion.CODE, 5.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice, bob),
                List.of(sprintId),
                recognized,
                Map.of(sprintId, Map.of(alice, 4.0, bob, 1.0)),
                ContributionSliceWeights.fromCourse(null)
        );

        assertEquals(70.5882, result.finalPercentageByStudent().get(alice), 0.001);
        assertEquals(29.4118, result.finalPercentageByStudent().get(bob), 0.001);
    }

    @Test
    void skipsSprintsWithNoRecognizedCriteria() {
        UUID alice = UUID.randomUUID();
        UUID emptySprint = UUID.randomUUID();
        UUID scoredSprint = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, scoredSprint, ContributionCriterion.CODE, 2.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice),
                List.of(emptySprint, scoredSprint),
                recognized,
                Map.of(),
                ContributionSliceWeights.fromCourse(null)
        );

        assertNull(result.percentageBySprintThenStudent().get(emptySprint));
        assertEquals(100.0, result.finalPercentageByStudent().get(alice), 0.0001);
    }

    @Test
    void usesIdentityPeerWhenASprintHasNoReviewsYet() {
        UUID alice = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, sprintId, ContributionCriterion.CODE, 5.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice),
                List.of(sprintId),
                recognized,
                Map.of(),
                ContributionSliceWeights.fromCourse(null)
        );

        assertEquals(100.0, result.percentageInSprint(sprintId, alice), 0.0001);
        assertEquals(100.0, result.finalPercentageByStudent().get(alice), 0.0001);
    }

    @Test
    void appliesProjectPeerToSummedSlicesNotSprintPercentageAverage() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID openSprint = UUID.randomUUID();
        UUID lastSprint = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, openSprint, ContributionCriterion.CODE, 10.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, lastSprint, ContributionCriterion.CODE, 3.0);
        SprintFirstContributionMixer.addRecognized(
                recognized, bob, lastSprint, ContributionCriterion.CODE, 5.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice, bob),
                List.of(openSprint, lastSprint),
                recognized,
                Map.of(lastSprint, Map.of(alice, 4.0, bob, 1.0)),
                ContributionSliceWeights.fromCourse(null)
        );

        assertEquals(100.0, result.percentageInSprint(openSprint, alice), 0.0001);
        assertEquals(0.0, result.percentageInSprint(openSprint, bob), 0.0001);
        assertEquals(70.5882, result.percentageInSprint(lastSprint, alice), 0.001);
        assertEquals(29.4118, result.percentageInSprint(lastSprint, bob), 0.001);
        // Default 25% CODE. Σslice Alice = 13×0.25 = 3.25; Bob = 5×0.25 = 1.25.
        // Project P 4/5 and 1/5 → adj 2.60 / 0.25 of 2.85.
        assertEquals(91.2281, result.finalPercentageByStudent().get(alice), 0.001);
        assertEquals(8.7719, result.finalPercentageByStudent().get(bob), 0.001);
    }

    @Test
    void matchesLockedSpecExampleAcrossFourSprints() {
        UUID an = UUID.randomUUID();
        UUID binh = UUID.randomUUID();
        UUID chi = UUID.randomUUID();
        UUID dung = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        UUID s4 = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();

        SprintFirstContributionMixer.addRecognized(recognized, an, s1, ContributionCriterion.CODE, 3);
        SprintFirstContributionMixer.addRecognized(recognized, an, s1, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, an, s1, ContributionCriterion.TEST, 3);
        SprintFirstContributionMixer.addRecognized(recognized, an, s1, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s1, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s1, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s1, ContributionCriterion.RESEARCH, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s1, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s1, ContributionCriterion.RESEARCH, 3);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s1, ContributionCriterion.CODE, 3);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s1, ContributionCriterion.CODE, 1);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s1, ContributionCriterion.TEST, 2);

        SprintFirstContributionMixer.addRecognized(recognized, an, s2, ContributionCriterion.CODE, 3);
        SprintFirstContributionMixer.addRecognized(recognized, an, s2, ContributionCriterion.RESEARCH, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s2, ContributionCriterion.CODE, 4);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s2, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s2, ContributionCriterion.TEST, 3);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s2, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s2, ContributionCriterion.TEST, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s2, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s2, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s2, ContributionCriterion.CODE, 1);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s2, ContributionCriterion.RESEARCH, 3);

        SprintFirstContributionMixer.addRecognized(recognized, an, s3, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, an, s3, ContributionCriterion.TEST, 2);
        SprintFirstContributionMixer.addRecognized(recognized, an, s3, ContributionCriterion.TEST, 2);
        SprintFirstContributionMixer.addRecognized(recognized, an, s3, ContributionCriterion.DOCUMENT, 3);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s3, ContributionCriterion.CODE, 4);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s3, ContributionCriterion.CODE, 3);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s3, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s3, ContributionCriterion.TEST, 1);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s3, ContributionCriterion.RESEARCH, 2);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s3, ContributionCriterion.TEST, 2);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s3, ContributionCriterion.DOCUMENT, 4);

        SprintFirstContributionMixer.addRecognized(recognized, an, s4, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, an, s4, ContributionCriterion.RESEARCH, 3);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s4, ContributionCriterion.DOCUMENT, 2);
        SprintFirstContributionMixer.addRecognized(recognized, binh, s4, ContributionCriterion.RESEARCH, 5);
        SprintFirstContributionMixer.addRecognized(recognized, chi, s4, ContributionCriterion.TEST, 3);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s4, ContributionCriterion.CODE, 4);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s4, ContributionCriterion.CODE, 2);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s4, ContributionCriterion.TEST, 2);
        SprintFirstContributionMixer.addRecognized(recognized, dung, s4, ContributionCriterion.DOCUMENT, 1);

        Map<UUID, Double> stars = Map.of(an, 4.0, binh, 3.0, chi, 2.0, dung, 1.0);
        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(an, binh, chi, dung),
                List.of(s1, s2, s3, s4),
                recognized,
                Map.of(s1, stars, s2, stars, s3, stars, s4, stars),
                SPEC_WEIGHTS
        );

        assertEquals(52.5253, result.percentageInSprint(s1, an), 0.01);
        assertEquals(19.6970, result.percentageInSprint(s1, binh), 0.01);
        assertEquals(18.6869, result.percentageInSprint(s1, chi), 0.01);
        assertEquals(9.0909, result.percentageInSprint(s1, dung), 0.01);

        assertEquals(38.6768, result.percentageInSprint(s2, an), 0.01);
        assertEquals(45.8015, result.percentageInSprint(s2, binh), 0.01);
        assertEquals(8.1425, result.percentageInSprint(s2, chi), 0.01);
        assertEquals(7.3791, result.percentageInSprint(s2, dung), 0.01);

        assertEquals(37.0787, result.percentageInSprint(s3, an), 0.01);
        assertEquals(26.9663, result.percentageInSprint(s3, binh), 0.01);
        assertEquals(31.4607, result.percentageInSprint(s3, chi), 0.01);
        assertEquals(4.4944, result.percentageInSprint(s3, dung), 0.01);

        assertEquals(43.7870, result.percentageInSprint(s4, an), 0.01);
        assertEquals(36.3905, result.percentageInSprint(s4, binh), 0.01);
        assertEquals(3.5503, result.percentageInSprint(s4, chi), 0.01);
        assertEquals(16.2722, result.percentageInSprint(s4, dung), 0.01);

        assertEquals(43.1558, result.finalPercentageByStudent().get(an), 0.01);
        assertEquals(32.1645, result.finalPercentageByStudent().get(binh), 0.01);
        assertEquals(15.5091, result.finalPercentageByStudent().get(chi), 0.01);
        assertEquals(9.1706, result.finalPercentageByStudent().get(dung), 0.01);

        assertEquals(8.00, result.sliceScore(an), 0.0001);
        assertEquals(7.95, result.sliceScore(binh), 0.0001);
        assertEquals(5.75, result.sliceScore(chi), 0.0001);
        assertEquals(6.80, result.sliceScore(dung), 0.0001);
        assertEquals(28.0702, result.slicePercentage(an), 0.01);
        assertEquals(27.8947, result.slicePercentage(binh), 0.01);
        assertEquals(20.1754, result.slicePercentage(chi), 0.01);
        assertEquals(23.8596, result.slicePercentage(dung), 0.01);
        assertEquals(2.60, result.sliceScoreInSprint(s1, an), 0.0001);
        assertEquals(34.4371, result.slicePercentageInSprint(s1, an), 0.01);
    }

    @Test
    void skipsNullSprintIds() {
        UUID alice = UUID.randomUUID();
        Map<UUID, Map<UUID, double[]>> recognized = new HashMap<>();
        SprintFirstContributionMixer.addRecognized(
                recognized, alice, null, ContributionCriterion.CODE, 5.0);

        SprintFirstContributionMixer.Result result = SprintFirstContributionMixer.mix(
                List.of(alice),
                Collections.singletonList(null),
                recognized,
                Map.of(),
                ContributionSliceWeights.fromCourse(null)
        );

        assertEquals(0.0, result.finalPercentageByStudent().get(alice), 0.0001);
    }
}
