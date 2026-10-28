package com.scorevault.rubric;

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
@RequestMapping("/api/v1")
public class RubricController {

    private final RubricService service;

    public RubricController(RubricService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return service.categories();
    }

    @GetMapping("/rubrics")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE')")
    public List<RubricView> list() {
        return service.list();
    }

    @PostMapping("/rubrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RubricView> create(@Valid @RequestBody RubricRequest request,
                                             Authentication authentication, HttpServletRequest http) {
        int userId = (Integer) authentication.getPrincipal();   // from the verified JWT
        RubricView created = service.create(request, userId, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
