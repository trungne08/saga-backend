package com.saga.be.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TaskDeadlineReminderSchedulerTest {
    private final TaskRepository tasks = Mockito.mock(TaskRepository.class);
    private final JiraMutationNotificationProducer producer = Mockito.mock(JiraMutationNotificationProducer.class);
    private final TaskDeadlineReminderScheduler scheduler = new TaskDeadlineReminderScheduler(
            tasks, producer, Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneId.of("UTC")), ZoneId.of("UTC"));

    @Test
    void dateOnlyCalendarTypesUseJiraZoneTodayTomorrowAndOverdue() {
        scheduler.remind(task(LocalDateTime.of(2026, 8, 12, 0, 0)));
        scheduler.remind(task(LocalDateTime.of(2026, 8, 11, 0, 0)));
        scheduler.remind(task(LocalDateTime.of(2026, 8, 10, 0, 0)));

        verify(producer).deadline(Mockito.any(Task.class), Mockito.eq(NotificationType.TASK_DUE_TOMORROW));
        verify(producer).deadline(Mockito.any(Task.class), Mockito.eq(NotificationType.TASK_DUE_TODAY));
        verify(producer).deadline(Mockito.any(Task.class), Mockito.eq(NotificationType.TASK_OVERDUE));
    }

    @Test
    void laterDateDoesNotProduceReminder() {
        scheduler.remind(task(LocalDateTime.of(2026, 8, 13, 0, 0)));
        verifyNoInteractions(producer);
    }

    @Test
    void usesConfiguredJiraCalendarZoneInsteadOfJvmDefault() {
        TaskDeadlineReminderScheduler hoChiMinhScheduler = new TaskDeadlineReminderScheduler(
                tasks,
                producer,
                Clock.fixed(Instant.parse("2026-08-11T23:30:00Z"), ZoneId.of("UTC")),
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        hoChiMinhScheduler.remind(task(LocalDateTime.of(2026, 8, 13, 0, 0)));

        verify(producer).deadline(Mockito.any(Task.class), Mockito.eq(NotificationType.TASK_DUE_TOMORROW));
    }

    @Test
    void oneTaskFailureDoesNotPreventAnotherReminder() {
        Task failing = task(LocalDateTime.of(2026, 8, 11, 0, 0));
        Task next = task(LocalDateTime.of(2026, 8, 10, 0, 0));
        doThrow(new IllegalStateException("persistence unavailable"))
                .when(producer).deadline(failing, NotificationType.TASK_DUE_TODAY);

        scheduler.remind(failing);
        scheduler.remind(next);

        verify(producer).deadline(next, NotificationType.TASK_OVERDUE);
    }

    @Test
    void scanUsesStableIdCursorInsteadOfMutableOffsetPages() {
        List<Task> firstBatch = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            Task task = task(LocalDateTime.of(2026, 8, 13, 12, 0));
            task.setId(new UUID(0, index));
            firstBatch.add(task);
        }
        UUID cursor = firstBatch.get(99).getId();
        Task finalTask = task(LocalDateTime.of(2026, 8, 13, 12, 0));
        finalTask.setId(new UUID(0, 101));
        when(tasks.findDeadlineEligibleTasksAfter(any(), isNull(), eq(org.springframework.data.domain.PageRequest.of(0, 100))))
                .thenReturn(firstBatch);
        when(tasks.findDeadlineEligibleTasksAfter(any(), eq(cursor), eq(org.springframework.data.domain.PageRequest.of(0, 100))))
                .thenReturn(List.of(finalTask));

        scheduler.scan();

        verify(tasks).findDeadlineEligibleTasksAfter(any(), isNull(), eq(org.springframework.data.domain.PageRequest.of(0, 100)));
        verify(tasks).findDeadlineEligibleTasksAfter(any(), eq(cursor), eq(org.springframework.data.domain.PageRequest.of(0, 100)));
    }

    private Task task(LocalDateTime dueDate) {
        Task task = new Task();
        task.setId(java.util.UUID.randomUUID());
        task.setProject(new Project());
        task.setDueDate(dueDate);
        return task;
    }
}
