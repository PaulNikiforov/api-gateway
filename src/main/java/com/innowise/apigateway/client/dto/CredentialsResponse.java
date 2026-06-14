package com.innowise.apigateway.client.dto;

/** Token pair response from Auth Service POST /api/v1/auth/credentials. */
public record CredentialsResponse(String accessToken, String refreshToken) {
}
