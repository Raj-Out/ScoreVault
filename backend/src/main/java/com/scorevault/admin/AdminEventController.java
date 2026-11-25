package com.scorevault.admin;

import java.util.List;
import java.util.Map;

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
@RequestMapping("/api/v1/admin")
public class AdminEventController {
    private final AdminEventService service;

    public AdminEventController(AdminEventService service) { this.service = service; }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EventOptionView> events() { return service.listEvents(); }

    @PostMapping("/rounds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> createRound(@Valid @RequestBody CreateRoundRequest request,
            Authentication authentication, HttpServletRequest http) {
        int id = service.createRound(request, (Integer) authentication.getPrincipal(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("roundId", id));
    }
}
