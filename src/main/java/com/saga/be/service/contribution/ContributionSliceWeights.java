package com.saga.be.service.contribution;

import com.saga.be.entity.Course;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Active Contribution criteria weights: Code/Test/Document/Research. DESIGN is retired as an
 * active criterion (it remains only a ProjectType catalog value); DESIGN-classified evidence is
 * folded into DOCUMENT upstream, not represented here.
 */
public record ContributionSliceWeights(
        BigDecimal code,
        BigDecimal test,
        BigDecimal document,
        BigDecimal research
) {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public static ContributionSliceWeights fromCourse(Course course) {
        if (course == null) {
            return defaultWeights();
        }
        return normalizeConfigured(
                decimal(course.getCodeContributionWeight()),
                decimal(course.getTestContributionWeight()),
                decimal(course.getDocumentContributionWeight()),
                decimal(course.getResearchContributionWeight())
        );
    }

    public static ContributionSliceWeights normalizeConfigured(
            BigDecimal code,
            BigDecimal test,
            BigDecimal document,
            BigDecimal research
    ) {
        BigDecimal safeCode = positiveOrZero(code);
        BigDecimal safeTest = positiveOrZero(test);
        BigDecimal safeDocument = positiveOrZero(document);
        BigDecimal safeResearch = positiveOrZero(research);
        BigDecimal total = safeCode.add(safeTest).add(safeDocument).add(safeResearch);
        if (total.signum() == 0) {
            return defaultWeights();
        }
        return new ContributionSliceWeights(
                safeCode.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED),
                safeTest.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED),
                safeDocument.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED),
                safeResearch.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED)
        );
    }

    public ContributionSliceWeights normalizeForActiveSlices(
            boolean codeActive,
            boolean testActive,
            boolean documentActive,
            boolean researchActive
    ) {
        return normalizeConfigured(
                codeActive ? code : BigDecimal.ZERO,
                testActive ? test : BigDecimal.ZERO,
                documentActive ? document : BigDecimal.ZERO,
                researchActive ? research : BigDecimal.ZERO
        );
    }

    public BigDecimal codeRatio() {
        return code.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal testRatio() {
        return test.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal documentRatio() {
        return document.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal researchRatio() {
        return research.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public double codeValue() {
        return code.doubleValue();
    }

    public double testValue() {
        return test.doubleValue();
    }

    public double documentValue() {
        return document.doubleValue();
    }

    public double researchValue() {
        return research.doubleValue();
    }

    private static ContributionSliceWeights defaultWeights() {
        return normalizeConfigured(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static BigDecimal positiveOrZero(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
