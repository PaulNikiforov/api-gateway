package com.innowise.apigateway.dto;

import java.time.Instant;

/** Uniform error envelope returned by Gateway for all 4xx/5xx responses. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {}
