package com.scorevault.auth;

public record UserRecord(int id, String fullName, String passwordHash, boolean active, String role) {
}
