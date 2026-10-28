package com.scorevault.auth;

public record LoginResponse(String token, UserInfo user) {
    public record UserInfo(int id, String name, String role) {
    }
}
