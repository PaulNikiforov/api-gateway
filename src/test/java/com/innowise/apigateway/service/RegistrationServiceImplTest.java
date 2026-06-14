package com.innowise.apigateway.service;

import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.service.impl.RegistrationServiceImpl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationServiceImplTest {

    private MockWebServer userServiceServer;
    private MockWebServer authServiceServer;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() throws IOException {
        userServiceServer = new MockWebServer();
        authServiceServer = new MockWebServer();
        userServiceServer.start();
        authServiceServer.start();

        WebClient userServiceWebClient = WebClient.builder()
                .baseUrl(userServiceServer.url("/").toString())
                .build();
        WebClient authServiceWebClient = WebClient.builder()
                .baseUrl(authServiceServer.url("/").toString())
                .build();

        registrationService = new RegistrationServiceImpl(userServiceWebClient, authServiceWebClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        userServiceServer.shutdown();
        authServiceServer.shutdown();
    }

    @Test
    void register_happyPath_returnsCombinedResponse() {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectNextMatches(response ->
                        response.userId() == 42L &&
                        "at".equals(response.accessToken()) &&
                        "rt".equals(response.refreshToken()))
                .verifyComplete();
    }

    @Test
    void register_authServiceFails_compensatesWithDeleteAndPropagatesError() throws InterruptedException {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(500));

        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(204));

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectError(WebClientResponseException.class)
                .verify();

        RecordedRequest firstRequest = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(firstRequest).isNotNull();
        assertThat(firstRequest.getMethod()).isEqualTo("POST");
        assertThat(firstRequest.getPath()).isEqualTo("/api/v1/users");

        RecordedRequest secondRequest = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(secondRequest).isNotNull();
        assertThat(secondRequest.getMethod()).isEqualTo("DELETE");
        assertThat(secondRequest.getPath()).isEqualTo("/api/v1/users/42");
    }

    @Test
    void register_propagatesConflict_whenAuthReturns409() throws InterruptedException {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(409));

        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(204));

        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectError(WebClientResponseException.Conflict.class)
                .verify();

        RecordedRequest createRequest = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(createRequest).isNotNull();
        assertThat(createRequest.getMethod()).isEqualTo("POST");

        RecordedRequest deleteRequest = userServiceServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(deleteRequest).isNotNull();
        assertThat(deleteRequest.getMethod()).isEqualTo("DELETE");
        assertThat(deleteRequest.getPath()).isEqualTo("/api/v1/users/42");
    }
}
