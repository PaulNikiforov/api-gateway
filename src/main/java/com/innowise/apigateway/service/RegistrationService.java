package com.innowise.apigateway.service;

import com.innowise.apigateway.GatewayConstants;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Synchronous reactive orchestration of user registration (Decision 3).
 * Step 1: {@code POST /api/v1/users} → User Service → {@code userId}.
 * Step 2: {@code POST /api/v1/auth/credentials} → Auth Service → token pair.
 *
 * <p>On Auth Service 5xx/network failures, two-step compensation is executed.
 * Auth Service 4xx errors (e.g. 409 Conflict) are propagated directly — no compensation runs.
 * <ol>
 *   <li>{@code PATCH /api/v1/users/{userId}/deactivate} — User Service only allows
 *       hard-deleting inactive users; deactivation must precede deletion.</li>
 *   <li>{@code DELETE /api/v1/users/{userId}} — removes the orphaned user record.</li>
 * </ol>
 * If either compensation step fails, the error is logged and swallowed (accepted gap).
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
                .bodyValue(new CreateUserRequest(request.name(), request.surname(), request.birthDate(), request.email()))
                .retrieve()
                .bodyToMono(UserCreatedResponse.class)
                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                .flatMap(userCreated ->
                        authServiceWebClient.post()
                                .uri("/api/v1/auth/credentials")
                                .bodyValue(new SaveCredentialsRequest(userCreated.userId(), request.email(), request.password()))
                                .retrieve()
                                .bodyToMono(CredentialsResponse.class)
                                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                                .map(creds -> new RegisterResponse(userCreated.userId(), creds.accessToken(), creds.refreshToken()))
                                .onErrorResume(authError -> {
                                    if (authError instanceof WebClientResponseException ex && ex.getStatusCode().is4xxClientError()) {
                                        return Mono.error(authError);
                                    }
                                    // Fire-and-forget: return error immediately, compensation runs in background (F5)
                                    compensate(userCreated.userId()).subscribe(
                                            null,
                                            err -> log.error("Background compensation failed for userId={}", userCreated.userId(), err)
                                    );
                                    return Mono.error(authError);
                                })
                );
    }

    private Mono<Void> compensate(Long userId) {
        return userServiceWebClient.patch()
                .uri("/api/v1/users/{id}/deactivate", userId)
                .retrieve()
                .toBodilessEntity()
                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                .onErrorResume(err -> {
                    log.error("Compensation: deactivate failed for userId={}", userId, err);
                    return Mono.empty();
                })
                .then(userServiceWebClient.delete()
                        .uri("/api/v1/users/{id}", userId)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                        .then())
                .onErrorResume(err -> {
                    log.error("Compensation: delete failed for userId={}", userId, err);
                    return Mono.empty();
                })
                // Aggregate ceiling: no single-step timeout should ever exceed 2×PER_CALL_TIMEOUT total (F8)
                .timeout(GatewayConstants.PER_CALL_TIMEOUT.multipliedBy(2))
                .onErrorResume(err -> {
                    log.error("Compensation: aggregate timeout exceeded for userId={}", userId, err);
                    return Mono.empty();
                });
    }
}
