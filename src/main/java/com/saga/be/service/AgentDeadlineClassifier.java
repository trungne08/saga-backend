package com.saga.be.service;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import java.time.LocalDate;

final class AgentDeadlineClassifier {

    static final String DUE_TOMORROW = "TASK_DUE_TOMORROW";
    static final String DUE_TODAY = "TASK_DUE_TODAY";
    static final String OVERDUE = "TASK_OVERDUE";

    private AgentDeadlineClassifier() {
    }

    static String classify(Task task, LocalDate today) {
        if (task == null || task.getDeletedAt() != null || task.getDueDate() == null || today == null) {
            return null;
        }
        if (task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED) {
            return null;
        }
        LocalDate due = task.getDueDate().toLocalDate();
        if (due.equals(today.plusDays(1))) {
            return DUE_TOMORROW;
        }
        if (due.equals(today)) {
            return DUE_TODAY;
        }
        if (due.isBefore(today)) {
            return OVERDUE;
        }
        return null;
    }
}
