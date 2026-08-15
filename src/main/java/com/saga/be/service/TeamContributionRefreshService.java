package com.saga.be.service;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TeamRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamContributionRefreshService {

    private final TeamRepository teamRepository;
    private final TeamContributionService teamContributionService;
    private final GitRepoRepository gitRepoRepository;
    private final JiraBoardRepository jiraBoardRepository;

    @Transactional(readOnly = true)
    public void refreshFromRepo(UUID repoId) {
        if (repoId == null) {
            return;
        }
        GitRepo repo = gitRepoRepository.findById(repoId).orElse(null);
        if (repo == null || repo.getProject() == null) {
            return;
        }
        refreshProject(repo.getProject().getId());
    }

    @Transactional(readOnly = true)
    public void refreshFromBoard(UUID boardId) {
        if (boardId == null) {
            return;
        }
        JiraBoard board = jiraBoardRepository.findById(boardId).orElse(null);
        if (board == null || board.getProject() == null) {
            return;
        }
        refreshProject(board.getProject().getId());
    }

    @Transactional(readOnly = true)
    public void refreshProject(UUID projectId) {
        if (projectId == null) {
            return;
        }
        teamRepository.findByProjectId(projectId)
                .ifPresent(team -> teamContributionService.evaluate(team.getId()));
    }
}
