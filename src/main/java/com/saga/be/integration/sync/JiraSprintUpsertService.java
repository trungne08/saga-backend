package com.saga.be.integration.sync;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Sprint;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraSprintSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JiraSprintUpsertService {

    private final JiraBoardRepository boardRepository;
    private final SprintRepository sprintRepository;
    private final TransactionTemplate transactionTemplate;

    public JiraSprintUpsertService(
            JiraBoardRepository boardRepository,
            SprintRepository sprintRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.boardRepository = boardRepository;
        this.sprintRepository = sprintRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Sprint upsert(UUID boardId, JiraSprintSnapshot snapshot) {
        try {
            return transactionTemplate.execute(status -> upsertAttempt(boardId, snapshot));
        } catch (DataIntegrityViolationException exception) {
            if (!isTargetDuplicate(exception)) throw exception;
            return transactionTemplate.execute(status -> reloadAndApply(boardId, snapshot));
        }
    }

    private Sprint upsertAttempt(UUID boardId, JiraSprintSnapshot snapshot) {
        JiraBoard board = boardRepository.findById(boardId).orElseThrow(() ->
                IntegrationException.invalid("JIRA_LINK_NOT_FOUND", "The Jira project link does not exist")
        );
        Sprint sprint = sprintRepository.findByBoardIdAndExternalSprintId(boardId, snapshot.id())
                .orElseGet(() -> Sprint.builder().board(board).externalSprintId(snapshot.id()).build());
        apply(sprint, snapshot);
        return sprintRepository.saveAndFlush(sprint);
    }

    private Sprint reloadAndApply(UUID boardId, JiraSprintSnapshot snapshot) {
        Sprint raced = sprintRepository.findByBoardIdAndExternalSprintId(boardId, snapshot.id())
                .orElseThrow(() -> IntegrationException.conflict("JIRA_SPRINT_UPSERT_CONFLICT", "The Jira sprint snapshot could not be reconciled"));
        apply(raced, snapshot);
        return sprintRepository.saveAndFlush(raced);
    }

    private boolean isTargetDuplicate(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("uk_sprint_board_external")) return true;
            current = current.getCause();
        }
        return false;
    }

    private void apply(Sprint sprint, JiraSprintSnapshot snapshot) {
        sprint.setName(snapshot.name());
        sprint.setState(snapshot.state());
        sprint.setGoal(snapshot.goal());
        sprint.setStartDate(snapshot.startDate());
        sprint.setEndDate(snapshot.endDate());
        sprint.setCompleteDate(snapshot.completeDate());
    }
}
