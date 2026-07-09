package com.innowise.apigateway.service;

import com.innowise.apigateway.AbstractDownstreamClientTest;
import com.innowise.apigateway.dto.RegisterRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationServiceTest extends AbstractDownstreamClientTest {

    @Test
    void register_whenBothServicesSucceed_shouldReturnCombinedResponse() {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectNextMatches(response ->
                        response.userId() == 42L &&
                        "at".equals(response.accessToken()) &&
                        "rt".equals(response.refreshToken()))
                .verifyComplete();
    }

    @Test
    void register_whenAuthServiceFails_shouldCompensateWithDeactivateThenDeleteAndPropagateError() throws InterruptedException {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(500));

        userServiceServer.enqueue(new MockResponse().setResponseCode(200));
        userServiceServer.enqueue(new MockResponse().setResponseCode(204));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectError(WebClientResponseException.class)
                .verify();

        RecordedRequest createRequest = takeNext(userServiceServer);
        assertThat(createRequest.getMethod()).isEqualTo("POST");
        assertThat(createRequest.getPath()).isEqualTo("/api/v1/users");

        RecordedRequest deactivateRequest = takeNext(userServiceServer);
        assertThat(deactivateRequest.getMethod()).isEqualTo("PATCH");
        assertThat(deactivateRequest.getPath()).isEqualTo("/api/v1/users/42/deactivate");

        RecordedRequest deleteRequest = takeNext(userServiceServer);
        assertThat(deleteRequest.getMethod()).isEqualTo("DELETE");
        assertThat(deleteRequest.getPath()).isEqualTo("/api/v1/users/42");
    }

    @Test
    void register_whenAuthReturns409_shouldPropagateConflictWithoutCompensation() throws InterruptedException {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":42}"));

        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(409));

        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, 1, 1), "alice@example.com", "password123");

        StepVerifier.create(registrationService.register(request))
                .expectError(WebClientResponseException.Conflict.class)
                .verify();

        assertThat(userServiceServer.getRequestCount()).isEqualTo(1);
        RecordedRequest createRequest = takeNext(userServiceServer);
        assertThat(createRequest.getMethod()).isEqualTo("POST");
        assertThat(createRequest.getPath()).isEqualTo("/api/v1/users");
    }
}
