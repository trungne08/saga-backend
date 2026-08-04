package com.saga.be.service.contribution;

import com.saga.be.entity.Course;
import java.math.BigDecimal;
import java.math.MathContext;

public record ContributionSliceWeights(
        BigDecimal code,
        BigDecimal document,
        BigDecimal design
) {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    public static ContributionSliceWeights fromCourse(Course course) {
        if (course == null) {
            return defaultWeights();
        }
        return normalizeConfigured(
                decimal(course.getCodeContributionWeight()),
                decimal(course.getDocumentContributionWeight()),
                decimal(course.getDesignContributionWeight())
        );
    }

    public static ContributionSliceWeights normalizeConfigured(
            BigDecimal code,
            BigDecimal document,
            BigDecimal design
    ) {
        BigDecimal safeCode = positiveOrZero(code);
        BigDecimal safeDocument = positiveOrZero(document);
        BigDecimal safeDesign = positiveOrZero(design);
        BigDecimal total = safeCode.add(safeDocument).add(safeDesign);
        if (total.signum() == 0) {
            return defaultWeights();
        }
        return new ContributionSliceWeights(
                safeCode.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED),
                safeDocument.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED),
                safeDesign.divide(total, MathContext.DECIMAL64).multiply(ONE_HUNDRED)
        );
    }

    public ContributionSliceWeights normalizeForActiveSlices(
            boolean codeActive,
            boolean documentActive,
            boolean designActive
    ) {
        return normalizeConfigured(
                codeActive ? code : BigDecimal.ZERO,
                documentActive ? document : BigDecimal.ZERO,
                designActive ? design : BigDecimal.ZERO
        );
    }

    public BigDecimal codeRatio() {
        return code.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal documentRatio() {
        return document.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public BigDecimal designRatio() {
        return design.divide(ONE_HUNDRED, MathContext.DECIMAL64);
    }

    public double codeValue() {
        return code.doubleValue();
    }

    public double documentValue() {
        return document.doubleValue();
    }

    public double designValue() {
        return design.doubleValue();
    }

    private static ContributionSliceWeights defaultWeights() {
        return normalizeConfigured(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
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
