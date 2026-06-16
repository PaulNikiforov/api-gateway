package com.innowise.apigateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.innowise.apigateway.AbstractDownstreamClientTest;
import com.innowise.apigateway.dto.RegisterRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exact requests Gateway sends to User Service.
 * Ensures {name, surname, birthDate, email} are forwarded, password is never exposed,
 * and compensation (PATCH deactivate → DELETE) is triggered on auth failure.
 */
class GatewayToUserServiceClientTest extends AbstractDownstreamClientTest {

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

        RecordedRequest recorded = takeNext(userServiceServer);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/users");

        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.get("name").asText()).isEqualTo("Alice");
        assertThat(body.get("surname").asText()).isEqualTo("Smith");
        assertThat(body.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(body.has("birthDate")).isTrue();
    }

    @Test
    void compensation_onAuthFailure_sendsDeactivateThenDeleteToUserService() throws InterruptedException {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":99}"));
        authServiceServer.enqueue(new MockResponse().setResponseCode(500));
        userServiceServer.enqueue(new MockResponse().setResponseCode(200));
        userServiceServer.enqueue(new MockResponse().setResponseCode(204));

        RegisterRequest request = new RegisterRequest(
                "Carol", "White", LocalDate.of(1992, 3, 10), "carol@example.com", "pass");

        StepVerifier.create(registrationService.register(request))
                .expectError()
                .verify();

        takeNext(userServiceServer); // POST /api/v1/users

        RecordedRequest deactivate = takeNext(userServiceServer);
        assertThat(deactivate.getMethod()).isEqualTo("PATCH");
        assertThat(deactivate.getPath()).isEqualTo("/api/v1/users/99/deactivate");

        RecordedRequest delete = takeNext(userServiceServer);
        assertThat(delete.getMethod()).isEqualTo("DELETE");
        assertThat(delete.getPath()).isEqualTo("/api/v1/users/99");
    }

    @Test
    void register_doesNotForwardPasswordToUserService() throws Exception {
        userServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":1}"));
        // auth stub needed to let the registration flow complete; only the user-service request body is asserted
        authServiceServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"accessToken\":\"at\",\"refreshToken\":\"rt\"}"));

        RegisterRequest request = new RegisterRequest(
                "Bob", "Jones", LocalDate.of(1990, 1, 1), "bob@example.com", "supersecret99");

        StepVerifier.create(registrationService.register(request))
                .expectNextCount(1)
                .verifyComplete();

        RecordedRequest recorded = takeNext(userServiceServer);
        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.has("password")).isFalse();
        assertThat(body.toString()).doesNotContain("supersecret99");
    }
}
