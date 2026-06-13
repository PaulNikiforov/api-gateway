package com.innowise.apigateway.client.dto;

/** Request body for POST /auth/credentials to Auth Service. password is plaintext — hashed by Auth Service. */
public record SaveCredentialsRequest(Long userId, String email, String password) {
}
