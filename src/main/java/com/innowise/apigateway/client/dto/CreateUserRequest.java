package com.innowise.apigateway.client.dto;

/** Request body for POST /api/users to User Service during registration orchestration. */
public record CreateUserRequest(String name, String email) {
}
