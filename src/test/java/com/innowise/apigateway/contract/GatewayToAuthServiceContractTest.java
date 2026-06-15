package com.innowise.apigateway.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.innowise.apigateway.client.AuthTokenValidationClient;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.service.RegistrationService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: verifies the exact request bodies Gateway sends to Auth Service.
 * Covers POST /api/v1/auth/credentials (registration) and POST /api/v1/auth/validate (token check).
 */
class GatewayToAuthServiceContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeAll
    static void warmUpNetty() throws IOException {
        try (MockWebServer warmup = new MockWebServer()) {
            warmup.start();
            warmup.enqueue(new MockResponse().setResponseCode(200));
            StepVerifier.create(
                    WebClient.builder().baseUrl(warmup.url("/").toString()).build()
                            .get().retrieve().toBodilessEntity()
            ).expectNextCount(1).expectComplete().verify(Duration.ofSeconds(30));
        }
    }

    private MockWebServer userServiceServer;
    private MockWebServer authServiceServer;
    private RegistrationService registrationService;
    private AuthTokenValidationClient validationClient;

    @BeforeEach
    void setUp() throws IOException {
        userServiceServer = new MockWebServer();
        authServiceServer = new MockWebServer();
        userServiceServer.start();
        authServiceServer.start();

        WebClient userClient = WebClient.builder().baseUrl(userServiceServer.url("/").toString()).build();
        WebClient authClient = WebClient.builder().baseUrl(authServiceServer.url("/").toString()).build();
        registrationService = new RegistrationService(userClient, authClient);
        validationClient = new AuthTokenValidationClient(authClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        userServiceServer.shutdown();
        authServiceServer.shutdown();
    }

    @Test
    void register_sendsUserIdEmailPasswordToAuthCredentials() throws Exception {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));
        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 6, 15), "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectNextCount(1)
                .verifyComplete();

        userServiceServer.takeRequest(1, TimeUnit.SECONDS);

        RecordedRequest authRequest = authServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(authRequest).isNotNull();
        assertThat(authRequest.getMethod()).isEqualTo("POST");
        assertThat(authRequest.getPath()).isEqualTo("/api/v1/auth/credentials");

        JsonNode body = MAPPER.readTree(authRequest.getBody().readByteArray());
        assertThat(body.get("userId").asLong()).isEqualTo(42L);
        assertThat(body.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(body.get("password").asText()).isEqualTo("password123");
    }

    @Test
    void validate_sendsAccessTokenToAuthValidate() throws Exception {
        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"userId\":7,\"role\":\"USER\"}"));

        StepVerifier.create(validationClient.validate("test-jwt-token"))
                .expectNextMatches(vr -> vr.userId() == 7L && "USER".equals(vr.role()))
                .verifyComplete();

        RecordedRequest recorded = authServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/auth/validate");

        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.get("accessToken").asText()).isEqualTo("test-jwt-token");
    }
}
