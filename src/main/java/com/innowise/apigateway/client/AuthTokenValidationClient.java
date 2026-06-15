package com.innowise.apigateway.client;

import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.exception.InvalidTokenException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client that delegates JWT validation to Auth Service via POST /api/v1/auth/validate.
 * Maps any 4xx/5xx response to {@link InvalidTokenException}.
 */
@Component
public class AuthTokenValidationClient {

    private final WebClient webClient;

    public AuthTokenValidationClient(@Qualifier("authServiceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<ValidationResponse> validate(String accessToken) {
        return webClient.post()
                .uri("/api/v1/auth/validate")
                .bodyValue(Map.of("accessToken", accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> Mono.error(new InvalidTokenException()))
                .bodyToMono(ValidationResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(e -> !(e instanceof InvalidTokenException), e -> new InvalidTokenException());
    }
}
