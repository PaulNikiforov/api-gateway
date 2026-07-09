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
                .timeout(GatewayConstants.PER_CALL_TIMEOUT.multipliedBy(2))
                .onErrorResume(err -> {
                    log.error("Compensation: aggregate timeout exceeded for userId={}", userId, err);
                    return Mono.empty();
                });
    }
}
