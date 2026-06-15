package com.innowise.apigateway.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Contract test: verifies the exact request body Gateway sends to User Service.
 * Ensures {name, surname, birthDate, email} are forwarded and password is never exposed.
 */
class GatewayToUserServiceContractTest {

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

    @BeforeEach
    void setUp() throws IOException {
        userServiceServer = new MockWebServer();
        authServiceServer = new MockWebServer();
        userServiceServer.start();
        authServiceServer.start();

        WebClient userClient = WebClient.builder().baseUrl(userServiceServer.url("/").toString()).build();
        WebClient authClient = WebClient.builder().baseUrl(authServiceServer.url("/").toString()).build();
        registrationService = new RegistrationService(userClient, authClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        userServiceServer.shutdown();
        authServiceServer.shutdown();
    }

    @Test
    void register_sendsNameSurnameBirthDateEmailToUserService() throws Exception {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":1}"));
        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 6, 15), "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectNextCount(1)
                .verifyComplete();

        RecordedRequest recorded = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/users");

        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.get("name").asText()).isEqualTo("Alice");
        assertThat(body.get("surname").asText()).isEqualTo("Smith");
        assertThat(body.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(body.has("birthDate")).isTrue();
    }

    @Test
    void register_doesNotForwardPasswordToUserService() throws Exception {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":1}"));
        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest(
                "Bob", "Jones", LocalDate.of(1990, 1, 1), "bob@example.com", "supersecret99");

        StepVerifier.create(registrationService.register(request))
                .expectNextCount(1)
                .verifyComplete();

        RecordedRequest recorded = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.has("password")).isFalse();
        assertThat(body.toString()).doesNotContain("supersecret99");
    }
}
