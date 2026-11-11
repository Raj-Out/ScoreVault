package com.scorevault.scoring;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoringController {

    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("hasRole('JUDGE')")
    public List<JudgeTaskView> myTasks(Authentication authentication) {
        int judgeUserId = (Integer) authentication.getPrincipal();
        return scoringService.listTasksForJudge(judgeUserId);
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('JUDGE')")
    public ResponseEntity<ScoredSummaryView> submitScores(
            @Valid @RequestBody SubmitScoreRequest request,
            Authentication authentication,
            HttpServletRequest http) {
        int judgeUserId = (Integer) authentication.getPrincipal();
        ScoredSummaryView summary = scoringService.submitAndLockScores(request, judgeUserId, http.getRemoteAddr());
        return ResponseEntity.ok(summary);
    }
}
