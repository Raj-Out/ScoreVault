package com.scorevault.submissions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class SubmissionController {
    private final SubmissionService service;

    public SubmissionController(SubmissionService service) { this.service = service; }

    @GetMapping("/teams/{teamId}/submissions")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public List<SubmissionView> list(@PathVariable int teamId, Authentication auth) {
        return service.listForTeam(teamId, (Integer) auth.getPrincipal());
    }

    @PostMapping(value = "/teams/{teamId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<SubmissionView> upload(@PathVariable int teamId,
            @RequestParam int roundId, @RequestParam MultipartFile file,
            @RequestParam(required = false) String notes, Authentication auth, HttpServletRequest http) {
        return ResponseEntity.status(201).body(service.upload(teamId, roundId, file, notes,
                (Integer) auth.getPrincipal(), http.getRemoteAddr()));
    }

    @GetMapping("/submissions/{submissionId}/file")
    @PreAuthorize("hasAnyRole('PARTICIPANT','JUDGE','ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable int submissionId, Authentication auth) {
        SubmissionDownload download = service.download(submissionId, (Integer) auth.getPrincipal(),
                auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.content().length)
                .body(download.content());
    }
}
