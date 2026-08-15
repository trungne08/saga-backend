package com.saga.be.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.early-warning.v2.processing-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EarlyWarningV2Scheduler {

    private static final Logger log = LoggerFactory.getLogger(EarlyWarningV2Scheduler.class);

    private final EarlyWarningV2Service detector;

    public EarlyWarningV2Scheduler(EarlyWarningV2Service detector) {
        this.detector = detector;
    }

    @Scheduled(
            fixedDelayString = "${app.early-warning.v2.scan-delay-ms:3600000}",
            initialDelayString = "${app.early-warning.v2.initial-delay-ms:60000}"
    )
    public void scan() {
        try {
            detector.scanBounded();
        } catch (RuntimeException exception) {
            log.warn("early-warning v2 scheduler failed type={}", exception.getClass().getSimpleName());
        }
    }
}
