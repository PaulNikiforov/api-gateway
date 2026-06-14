package com.innowise.apigateway.client.dto;

/** Token validation response from Auth Service POST /api/v1/auth/validate. */
public record ValidationResponse(Long userId, String role) {
}
