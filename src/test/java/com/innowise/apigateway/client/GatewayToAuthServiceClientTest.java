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

class GatewayToAuthServiceClientTest extends AbstractDownstreamClientTest {

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

        takeNext(userServiceServer);

        RecordedRequest authRequest = takeNext(authServiceServer);
        assertThat(authRequest.getMethod()).isEqualTo("POST");
        assertThat(authRequest.getPath()).isEqualTo("/api/v1/auth/credentials");

        JsonNode body = MAPPER.readTree(authRequest.getBody().readByteArray());
        assertThat(body.get("userId").asLong()).isEqualTo(42L);
        assertThat(body.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(body.get("password").asText()).isEqualTo("password123");
    }
}
