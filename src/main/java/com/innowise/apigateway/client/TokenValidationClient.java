package com.innowise.apigateway.client;

import com.innowise.apigateway.client.dto.ValidationResponse;
import reactor.core.publisher.Mono;

/** Client contract for delegating JWT validation to Auth Service. */
public interface TokenValidationClient {

    /**
     * Validates the given access token by calling Auth Service POST /auth/validate.
     *
     * @param accessToken the raw JWT access token to validate
     * @return a {@link Mono} emitting {@link ValidationResponse} on success,
     *         or {@link com.innowise.apigateway.exception.InvalidTokenException} on any error response
     */
    Mono<ValidationResponse> validate(String accessToken);
}
