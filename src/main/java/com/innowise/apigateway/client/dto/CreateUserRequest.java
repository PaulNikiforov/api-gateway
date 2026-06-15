package com.innowise.apigateway.client.dto;

import java.time.LocalDate;

/** Request body for POST /api/v1/users to User Service during registration orchestration. */
public record CreateUserRequest(String name, String surname, LocalDate birthDate, String email) {
}
