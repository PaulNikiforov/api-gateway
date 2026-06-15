package com.innowise.apigateway.service;

import com.innowise.apigateway.client.dto.CreateUserRequest;
import com.innowise.apigateway.client.dto.CredentialsResponse;
import com.innowise.apigateway.client.dto.SaveCredentialsRequest;
import com.innowise.apigateway.client.dto.UserCreatedResponse;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.dto.RegisterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Synchronous reactive orchestration of user registration (Decision 3).
 * Step 1: {@code POST /api/v1/users} → User Service → {@code userId}.
 * Step 2: {@code POST /api/v1/auth/credentials} → Auth Service → token pair.
 * On any Auth Service failure (including non-2xx responses such as 409):
 * compensating {@code DELETE /api/v1/users/{userId}} is sent;
 * if the compensation DELETE also fails, the error is logged and swallowed (accepted gap).
 */
@Slf4j
@Service
public class RegistrationService {

    private final WebClient userServiceWebClient;
    private final WebClient authServiceWebClient;

    public RegistrationService(
            @Qualifier("userServiceWebClient") WebClient userServiceWebClient,
            @Qualifier("authServiceWebClient") WebClient authServiceWebClient) {
        this.userServiceWebClient = userServiceWebClient;
        this.authServiceWebClient = authServiceWebClient;
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        return userServiceWebClient.post()
                .uri("/api/v1/users")
                .bodyValue(new CreateUserRequest(request.name(), request.email()))
                .retrieve()
                .bodyToMono(UserCreatedResponse.class)
                .timeout(Duration.ofSeconds(5))
                .flatMap(userCreated ->
                        authServiceWebClient.post()
                                .uri("/api/v1/auth/credentials")
                                .bodyValue(new SaveCredentialsRequest(userCreated.userId(), request.email(), request.password()))
                                .retrieve()
                                .bodyToMono(CredentialsResponse.class)
                                .timeout(Duration.ofSeconds(5))
                                .map(creds -> new RegisterResponse(userCreated.userId(), creds.accessToken(), creds.refreshToken()))
                                .onErrorResume(authError ->
                                        deleteUser(userCreated.userId())
                                                .then(Mono.error(authError)))
                );
    }

    private Mono<Void> deleteUser(Long userId) {
        return userServiceWebClient.delete()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(delErr -> {
                    log.error("Compensation DELETE failed for userId={}", userId, delErr);
                    return Mono.empty();
                })
                .then();
    }
}
