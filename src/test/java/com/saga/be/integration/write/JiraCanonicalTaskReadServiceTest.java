package com.saga.be.integration.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.repository.TaskRepository;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class JiraCanonicalTaskReadServiceTest {

    @Test
    void confirmationReaderUsesFreshReadOnlyTransactionAndMapsInsideIt() throws Exception {
        TaskRepository tasks = mock(TaskRepository.class);
        UUID projectId = UUID.randomUUID();
        Task task = Task.builder().project(Project.builder().build()).externalId("101").title("Task").build();
        task.getProject().setId(projectId);
        task.setId(UUID.randomUUID());
        when(tasks.findByProjectIdAndExternalId(projectId, "101")).thenReturn(Optional.of(task));

        assertEquals(task.getId(), new JiraCanonicalTaskReadService(tasks)
                .findResponse(projectId, "101").orElseThrow().id());
        verify(tasks).findByProjectIdAndExternalId(projectId, "101");

        Method method = JiraCanonicalTaskReadService.class.getMethod("findResponse", UUID.class, String.class);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
        assertTrue(transaction.readOnly());

        Method exists = JiraCanonicalTaskReadService.class.getMethod("exists", UUID.class, String.class);
        Transactional existenceTransaction = exists.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, existenceTransaction.propagation());
        assertTrue(existenceTransaction.readOnly());
    }
}
