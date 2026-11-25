package com.scorevault.admin;

public record AdminUserView(int userId, String fullName, String email, String affiliation,
        String role, boolean active) {}
