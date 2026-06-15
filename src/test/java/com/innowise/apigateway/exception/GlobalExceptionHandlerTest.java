package com.innowise.apigateway.exception;

import com.innowise.apigateway.controller.RegistrationController;
import com.innowise.apigateway.dto.ErrorResponse;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(RegistrationController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void register_whenBlankName_shouldReturn400WithErrorResponseMessageNotNull() {
        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(body -> {
                    if (body.message() == null) {
                        throw new AssertionError(
                                "Expected ErrorResponse.message() to be non-null, but was null. " +
                                "GlobalExceptionHandler is likely missing."
                        );
                    }
                });
    }

    @Test
    void register_whenDownstreamReturns409_shouldReturn409() {
        when(registrationService.register(any()))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        409, "Conflict", null, null, null)));

        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void register_whenUnexpectedExceptionThrown_shouldReturn500() {
        when(registrationService.register(any()))
                .thenReturn(Mono.error(new RuntimeException("unexpected")));

        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123"))
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody(ErrorResponse.class)
                .value(body -> {
                    if (body.message() == null) {
                        throw new AssertionError("Expected ErrorResponse.message() to be non-null");
                    }
                });
    }
}
