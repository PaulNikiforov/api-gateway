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
import reactor.util.retry.Retry;

import java.util.UUID;

@Slf4j
@Service
public class RegistrationService {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final Retry DOWNSTREAM_RETRY = Retry
            .backoff(GatewayConstants.DOWNSTREAM_RETRIES, GatewayConstants.RETRY_MIN_BACKOFF)
            .filter(RegistrationService::isRetryable)
            .onRetryExhaustedThrow((spec, signal) -> signal.failure());

    private final WebClient userServiceWebClient;
    private final WebClient authServiceWebClient;

    public RegistrationService(
            @Qualifier("userServiceWebClient") WebClient userServiceWebClient,
            @Qualifier("authServiceWebClient") WebClient authServiceWebClient) {
        this.userServiceWebClient = userServiceWebClient;
        this.authServiceWebClient = authServiceWebClient;
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        var idempotencyKey = UUID.randomUUID().toString();
        return userServiceWebClient.post()
                .uri("/api/v1/users")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .bodyValue(new CreateUserRequest(request.name(), request.surname(), request.birthDate(), request.email()))
                .retrieve()
                .bodyToMono(UserCreatedResponse.class)
                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                .retryWhen(DOWNSTREAM_RETRY)
                .flatMap(userCreated ->
                        authServiceWebClient.post()
                                .uri("/api/v1/auth/credentials")
                                .bodyValue(new SaveCredentialsRequest(userCreated.userId(), request.email(), request.password()))
                                .retrieve()
                                .bodyToMono(CredentialsResponse.class)
                                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                                .retryWhen(DOWNSTREAM_RETRY)
                                .map(creds -> new RegisterResponse(userCreated.userId(), creds.accessToken(), creds.refreshToken()))
                                .onErrorResume(authError -> {
                                    if (authError instanceof WebClientResponseException ex && ex.getStatusCode().is4xxClientError()) {
                                        return Mono.error(authError);
                                    }
                                    compensate(userCreated.userId()).subscribe(
                                            null,
                                            err -> log.error("Background compensation failed for userId={}", userCreated.userId(), err)
                                    );
                                    return Mono.error(authError);
                                })
                );
    }

    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return true;
    }

    private Mono<Void> compensate(Long userId) {
        return userServiceWebClient.patch()
                .uri("/api/v1/users/{id}/deactivate", userId)
                .retrieve()
                .toBodilessEntity()
                .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                .retryWhen(DOWNSTREAM_RETRY)
                .onErrorResume(err -> {
                    log.error("Compensation: deactivate failed for userId={}", userId, err);
                    return Mono.empty();
                })
                .then(userServiceWebClient.delete()
                        .uri("/api/v1/users/{id}", userId)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(GatewayConstants.PER_CALL_TIMEOUT)
                        .retryWhen(DOWNSTREAM_RETRY)
                        .then())
                .onErrorResume(err -> {
                    log.error("Compensation: delete failed for userId={}", userId, err);
                    return Mono.empty();
                })
                .timeout(GatewayConstants.COMPENSATION_TIMEOUT)
                .onErrorResume(err -> {
                    log.error("Compensation: aggregate timeout exceeded for userId={}", userId, err);
                    return Mono.empty();
                });
    }
}
