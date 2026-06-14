package com.innowise.apigateway.controller;

import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.dto.RegisterResponse;
import com.innowise.apigateway.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Handles {@code POST /api/v1/register}. Not proxied — served directly by Gateway.
 * Listed in public whitelist; no JWT required.
 */
@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * Orchestrates user registration: step 1 creates a user profile in User Service
     * ({@code POST /api/v1/users}), step 2 saves credentials in Auth Service
     * ({@code POST /api/v1/auth/credentials}). On Auth Service failure a compensating
     * {@code DELETE /api/v1/users/{userId}} is issued before the error propagates.
     * Returns 201 with {@link RegisterResponse} on success.
     * Possible responses: 400 (validation), 409 (duplicate credentials), 5xx (downstream failure).
     */
    @PostMapping("/api/v1/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
