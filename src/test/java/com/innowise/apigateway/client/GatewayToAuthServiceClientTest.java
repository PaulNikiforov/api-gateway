package com.innowise.apigateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.innowise.apigateway.AbstractDownstreamClientTest;
import com.innowise.apigateway.dto.RegisterRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exact request bodies Gateway sends to Auth Service.
 * Covers POST /api/v1/auth/credentials (registration) and POST /api/v1/auth/validate (token check).
 */
class GatewayToAuthServiceClientTest extends AbstractDownstreamClientTest {

    private AuthTokenValidationClient validationClient;

    @Override
    protected void onSetUp(WebClient userClient, WebClient authClient) {
        validationClient = new AuthTokenValidationClient(authClient);
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

        takeNext(userServiceServer); // POST /api/v1/users (discard — asserted in GatewayToUserServiceClientTest)

        RecordedRequest authRequest = takeNext(authServiceServer);
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

        RecordedRequest recorded = takeNext(authServiceServer);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/auth/validate");

        JsonNode body = MAPPER.readTree(recorded.getBody().readByteArray());
        assertThat(body.get("accessToken").asText()).isEqualTo("test-jwt-token");
    }
}
