package com.innowise.apigateway.client.dto;

public record SaveCredentialsRequest(Long userId, String email, String password) {
}
