package com.scorevault.teams;

import java.util.List;

import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/teams")
public class TeamController {
    private final TeamService service;

    public TeamController(TeamService service) { this.service = service; }

    @GetMapping("/events")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public List<EventTeamOption> events() { return service.listOpenEvents(); }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public List<TeamView> mine(Authentication authentication) {
        return service.listMine((Integer) authentication.getPrincipal());
    }

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<TeamView> create(@Valid @RequestBody CreateTeamRequest request,
            Authentication authentication, HttpServletRequest http) {
        TeamView team = service.create(request, (Integer) authentication.getPrincipal(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(team);
    }
}
