package com.innowise.apigateway.client.dto;

/** Token pair response from Auth Service POST /auth/credentials. */
public record CredentialsResponse(String accessToken, String refreshToken) {
}
