package com.scorevault.judging;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/judging")
public class JudgeAssignmentController {

    private final JudgeAssignmentService assignmentService;

    public JudgeAssignmentController(JudgeAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/judges")
    @PreAuthorize("hasRole('ADMIN')")
    public List<JudgeUserView> listJudges() {
        return assignmentService.listJudges();
    }

    @GetMapping("/teams/{roundId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TeamOptionView> listTeams(@PathVariable int roundId) {
        return assignmentService.listTeamsForRound(roundId);
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<JudgeAssignmentView> listAssignments(@RequestParam(required = false) Integer roundId) {
        return assignmentService.listAssignments(roundId);
    }

    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JudgeAssignmentView> assign(
            @Valid @RequestBody AssignJudgeRequest req,
            Authentication authentication,
            HttpServletRequest http) {
        int adminUserId = (Integer) authentication.getPrincipal();
        JudgeAssignmentView created = assignmentService.assignJudge(req, adminUserId, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
