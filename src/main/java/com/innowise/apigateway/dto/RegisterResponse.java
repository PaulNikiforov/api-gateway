package com.innowise.apigateway.dto;

public record RegisterResponse(Long userId, String accessToken, String refreshToken) {}
