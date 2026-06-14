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

    /** Delegates to {@link RegistrationService#register}; returns HTTP 201 on success. */
    @PostMapping("/api/v1/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
