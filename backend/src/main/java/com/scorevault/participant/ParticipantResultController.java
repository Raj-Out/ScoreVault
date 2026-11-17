package com.scorevault.participant;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant")
@PreAuthorize("hasRole('PARTICIPANT')")
public class ParticipantResultController {

    private final ParticipantResultService results;

    public ParticipantResultController(ParticipantResultService results) {
        this.results = results;
    }

    // The user id comes from the verified JWT; there is no team or user id in the request.
    @GetMapping("/results")
    public List<TeamResultView> myResults(Authentication auth) {
        return results.myResults((Integer) auth.getPrincipal());
    }
}