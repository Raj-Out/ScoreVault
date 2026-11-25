package com.scorevault.participant;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant")
@PreAuthorize("hasRole('PARTICIPANT')")
public class ParticipantController {

    private final ParticipantTeamService teams;

    public ParticipantController(ParticipantTeamService teams) {
        this.teams = teams;
    }

    // The user id always comes from the verified JWT, never from the request.
    private int me(Authentication auth) {
        return (Integer) auth.getPrincipal();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamView createTeam(@RequestBody CreateTeamRequest req, Authentication auth) {
        return teams.createTeam(me(auth), req);
    }

    @GetMapping("/teams")
    public List<TeamView> myTeams(Authentication auth) {
        return teams.listMyTeams(me(auth));
    }

    @PostMapping("/teams/{teamId}/members")
    public TeamView addMember(@PathVariable int teamId, @RequestBody AddMemberRequest req,
                              Authentication auth) {
        return teams.addMember(me(auth), teamId, req.email());
    }
}