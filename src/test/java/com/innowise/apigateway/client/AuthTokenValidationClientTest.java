package com.innowise.apigateway.client;

import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.exception.InvalidTokenException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenValidationClientTest {

    private MockWebServer mockWebServer;
    private TokenValidationClient tokenValidationClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        tokenValidationClient = new AuthTokenValidationClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void validate_whenTokenIsValid_returnsValidationResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"userId\":42,\"role\":\"USER\"}"));

        StepVerifier.create(tokenValidationClient.validate("valid-token"))
                .assertNext(response -> {
                    assertThat(response.userId()).isEqualTo(42L);
                    assertThat(response.role()).isEqualTo("USER");
                })
                .verifyComplete();
    }

    @Test
    void validate_whenTokenIsInvalid_throwsInvalidTokenException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Token is invalid\"}"));

        StepVerifier.create(tokenValidationClient.validate("invalid-token"))
                .expectError(InvalidTokenException.class)
                .verify();
    }
}
