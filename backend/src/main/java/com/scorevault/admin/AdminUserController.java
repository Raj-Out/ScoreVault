package com.scorevault.admin;

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
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public List<AdminUserView> list() { return service.listUsers(); }

    @PostMapping
    public ResponseEntity<AdminUserView> create(@Valid @RequestBody CreateUserRequest request,
            Authentication authentication, HttpServletRequest http) {
        AdminUserView user = service.createUser(request,
                (Integer) authentication.getPrincipal(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
