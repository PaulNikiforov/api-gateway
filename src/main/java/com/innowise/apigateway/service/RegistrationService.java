package com.innowise.apigateway.service;

import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.dto.RegisterResponse;
import reactor.core.publisher.Mono;

/**
 * Service for orchestrating user registration across User Service and Auth Service.
 */
public interface RegistrationService {

    /**
     * Registers a new user by creating a profile in User Service and credentials in Auth Service.
     *
     * @param request the registration request containing name, email, and password
     * @return a {@link Mono} emitting the combined registration response with userId and tokens
     */
    Mono<RegisterResponse> register(RegisterRequest request);
}
