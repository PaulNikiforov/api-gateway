package com.innowise.apigateway.controller;

import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.dto.RegisterResponse;
import com.innowise.apigateway.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(RegistrationController.class)
class RegistrationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void register_whenBlankName_shouldReturn400() {
        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void register_whenShortPassword_shouldReturn400() {
        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "pass"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void register_whenValidRequest_shouldReturn201WithRegistrationResponse() {
        RegisterResponse mockResponse = new RegisterResponse(42L, "access-token", "refresh-token");
        given(registrationService.register(any(RegisterRequest.class)))
                .willReturn(Mono.just(mockResponse));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123");

        webTestClient.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(42)
                .jsonPath("$.accessToken").isEqualTo("access-token")
                .jsonPath("$.refreshToken").isEqualTo("refresh-token");
    }
}
