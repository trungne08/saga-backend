package com.saga.be.service;

import java.time.Duration;

public final class EarlyWarningPolicy {

    public static final Duration ACTIVITY_WINDOW = Duration.ofHours(72);
    public static final double SPRINT_START_EVALUATION = 0.40d;
    public static final double SPRINT_WARNING_GAP = 0.25d;
    public static final double SPRINT_CRITICAL_GAP = 0.40d;
    public static final int REPEATED_WINDOW = 3;
    public static final int REPEATED_THRESHOLD = 2;
    public static final int HISTORICAL_DISCOVERY_PAGE = 20;
    public static final int HISTORICAL_DIGEST_REPO_LIMIT = 20;

    public static final String INACTIVITY_GRACE_PERIOD = "TBD_PRODUCT";

    private EarlyWarningPolicy() {
    }
}
