package com.saga.be.service;

import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Date-only Jira due-date reminders; never infers a due-time or JVM-default zone. */
@Component
@ConditionalOnProperty(name = "app.notification.deadline.processing-enabled", havingValue = "true", matchIfMissing = true)
public class TaskDeadlineReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskDeadlineReminderScheduler.class);
    private final TaskRepository tasks;
    private final JiraMutationNotificationProducer producer;
    private final Clock clock;
    private final ZoneId jiraZone;

    @Autowired
    public TaskDeadlineReminderScheduler(TaskRepository tasks, JiraMutationNotificationProducer producer,
            JiraTimeZoneProperties zoneProperties) {
        this(tasks, producer, Clock.systemUTC(), ZoneId.of(zoneProperties.timeZone()));
    }

    TaskDeadlineReminderScheduler(TaskRepository tasks, JiraMutationNotificationProducer producer, Clock clock, ZoneId jiraZone) {
        this.tasks = tasks; this.producer = producer; this.clock = clock; this.jiraZone = jiraZone;
    }

    @Scheduled(fixedDelayString = "${app.notification.deadline.scan-delay-ms:3600000}")
    public void scan() {
        UUID afterId = null;
        while (true) {
            var result = tasks.findDeadlineEligibleTasksAfter(
                    List.of(TaskStatus.DONE, TaskStatus.CANCELLED), afterId, PageRequest.of(0, 100));
            result.forEach(this::remind);
            if (result.size() < 100) return;
            afterId = result.get(result.size() - 1).getId();
        }
    }

    void remind(Task task) {
        LocalDate today = LocalDate.now(clock.withZone(jiraZone));
        LocalDate due = task.getDueDate().toLocalDate();
        NotificationType type = due.equals(today.plusDays(1)) ? NotificationType.TASK_DUE_TOMORROW
                : due.equals(today) ? NotificationType.TASK_DUE_TODAY
                : due.isBefore(today) ? NotificationType.TASK_OVERDUE : null;
        if (type == null) return;
        try { producer.deadline(task, type); }
        catch (RuntimeException exception) { log.warn("notification producer=TASK_DEADLINE stage=PERSIST result=FAILED exceptionClass={}", exception.getClass().getSimpleName()); }
    }
}
