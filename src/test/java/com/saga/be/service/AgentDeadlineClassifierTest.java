package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AgentDeadlineClassifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    @Test
    void classifiesDateOnlyDueSignalsAndSkipsDoneOrCancelled() {
        assertEquals(AgentDeadlineClassifier.DUE_TODAY, AgentDeadlineClassifier.classify(task(
                TaskStatus.TODO, TODAY.atStartOfDay()
        ), TODAY));
        assertEquals(AgentDeadlineClassifier.DUE_TOMORROW, AgentDeadlineClassifier.classify(task(
                TaskStatus.IN_PROGRESS, TODAY.plusDays(1).atTime(23, 59)
        ), TODAY));
        assertEquals(AgentDeadlineClassifier.OVERDUE, AgentDeadlineClassifier.classify(task(
                TaskStatus.IN_REVIEW, TODAY.minusDays(1).atStartOfDay()
        ), TODAY));
        assertNull(AgentDeadlineClassifier.classify(task(TaskStatus.DONE, TODAY.minusDays(2).atStartOfDay()), TODAY));
        assertNull(AgentDeadlineClassifier.classify(task(TaskStatus.CANCELLED, TODAY.atStartOfDay()), TODAY));
        assertNull(AgentDeadlineClassifier.classify(task(TaskStatus.TODO, TODAY.plusDays(2).atStartOfDay()), TODAY));
    }

    private Task task(TaskStatus status, LocalDateTime dueDate) {
        return Task.builder().status(status).dueDate(dueDate).title("T").build();
    }
}
