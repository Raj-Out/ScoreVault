package com.scorevault.participant;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/participant")
@PreAuthorize("hasRole('PARTICIPANT')")
public class ParticipantSubmissionController {

    private final ParticipantSubmissionService submissions;

    public ParticipantSubmissionController(ParticipantSubmissionService submissions) {
        this.submissions = submissions;
    }

    // The user id comes from the verified JWT, never from the request.
    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionView upload(@RequestParam int teamId, @RequestParam int roundId,
                                 @RequestParam("file") MultipartFile file, Authentication auth) {
        return submissions.submit((Integer) auth.getPrincipal(), teamId, roundId, file);
    }

    @GetMapping("/submissions")
    public List<SubmissionView> mine(Authentication auth) {
        return submissions.mine((Integer) auth.getPrincipal());
    }
}