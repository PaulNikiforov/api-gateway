package com.innowise.apigateway.config;

import com.innowise.apigateway.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Signals a 401 as a {@link ResponseStatusException} instead of writing a response directly, so
 * that {@link com.innowise.apigateway.exception.GatewayErrorWebExceptionHandler} renders it into
 * the same {@link ErrorResponse} JSON envelope used for every other Gateway error — avoiding a
 * second, parallel implementation of that envelope here.
 */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token"));
    }
}
