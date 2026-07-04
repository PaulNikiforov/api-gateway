package com.innowise.apigateway.config;

import com.innowise.apigateway.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void register_whenNoAuthToken_shouldNotBeRejectedBySecurityFilterChain() {
        var result = webTestClient.post().uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .returnResult(Void.class);

        assertThat(result.getStatus().value()).isNotIn(401, 403);
    }

    @Test
    void protectedEndpoint_whenNoAuthToken_shouldReturn401WithErrorResponseBody() {
        webTestClient.get().uri("/api/v1/users/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(body -> {
                    assertThat(body.status()).isEqualTo(401);
                    assertThat(body.message()).isNotBlank();
                    assertThat(body.path()).isEqualTo("/api/v1/users/1");
                });
    }
}
