package com.innowise.apigateway.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System E2E test — golden path only.
 *
 * <p>Starts the full platform via docker-compose and exercises the three journeys that:
 * (a) cross 2+ services through the Gateway, and (b) would cause immediate business impact
 * if broken silently. Error scenarios (401, 409, 404) are covered at unit/integration level.
 *
 * <p><b>Prerequisite</b>: build images once with {@code docker compose build} from
 * {@code /Innowise/}.
 *
 * <p><b>Run</b>: {@code mvn failsafe:integration-test failsafe:verify}
 *
 * <p>Port 8090 is the host-side mapping for api-gateway (8090:8080 in docker-compose.yml).
 * {@code withExposedService} is intentionally absent: Docker Compose v2 names containers
 * with hyphens ({@code ...-api-gateway-1}) but the Testcontainers socat ambassador uses
 * underscores ({@code ..._api-gateway_1}), causing a link failure. Mapped host port is used
 * directly instead.
 *
 * <p>See: <a href="../../../../../../../../../MICROSERVICES-TESTING.md">MICROSERVICES-TESTING.md §5</a>
 */
@Tag("e2e")
@Testcontainers
class SystemE2ETest {

    private static final String GATEWAY_BASE_URL = "http://localhost:8090";

    private static final String TEST_EMAIL = "e2e_golden@example.com";
    private static final String TEST_PASSWORD = "Password1!";

    /**
     * JWT_SECRET override: docker-compose default uses a placeholder.
     * A valid base64-encoded 32-byte key is required for authservice HS256.
     */
    @Container
    static final DockerComposeContainer<?> ENVIRONMENT = new DockerComposeContainer<>(
            new File("../docker-compose.yml")
    )
            .withLocalCompose(true)
            .withEnv("JWT_SECRET", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    static WebTestClient client;
    static long registeredUserId;
    static String accessToken;

    /**
     * Waits for api-gateway health, then registers a shared user for all tests.
     * Shared state avoids 3 separate registration calls (each takes ~500 ms cross-service).
     */
    @BeforeAll
    static void setUpGoldenPathUser() throws Exception {
        client = WebTestClient.bindToServer()
                .baseUrl(GATEWAY_BASE_URL)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        waitForGatewayHealth();

        Map<String, Object> body = client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "Golden",
                        "surname", "Path",
                        "birthDate", "1990-01-01",
                        "email", TEST_EMAIL,
                        "password", TEST_PASSWORD
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        registeredUserId = ((Number) body.get("userId")).longValue();
        accessToken = (String) body.get("accessToken");
    }

    // ── Critical path 1: Registration (Gateway → User Service → Auth Service) ───

    /**
     * Full registration flow crosses 3 services: Gateway orchestrates calls to
     * User Service (create user) and Auth Service (create credentials), then
     * assembles the response. Cannot be decomposed into contract tests alone.
     */
    @Test
    void register_createsUserAndReturnsTokens() {
        String uniqueEmail = "e2e_reg_" + System.nanoTime() + "@example.com";

        Map<String, Object> body = client.post()
                .uri("/api/v1/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "New",
                        "surname", "User",
                        "birthDate", "1995-06-15",
                        "email", uniqueEmail,
                        "password", TEST_PASSWORD
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.get("userId")).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("refreshToken")).isNotNull();
    }

    // ── Critical path 2: Authentication (Gateway → Auth Service) ─────────────────

    /**
     * Login flow: Gateway proxies to Auth Service and returns tokens.
     * Verifies that the route config, JWT secret propagation, and response
     * mapping all work end-to-end.
     */
    @Test
    void login_withValidCredentials_returnsTokens() {
        client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", TEST_EMAIL, "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty();
    }

    // ── Critical path 3: Authenticated resource access (Gateway JWT filter → User Service) ─

    /**
     * Protected GET crosses 2 services: Gateway validates JWT against Auth Service
     * (POST /api/v1/auth/validate), injects X-User-Id / X-User-Role headers, then
     * forwards to User Service. Verifies the full auth filter + routing chain.
     */
    @Test
    void getUser_withValidJwtToken_returnsUserData() {
        client.get()
                .uri("/api/v1/users/" + registeredUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(registeredUserId)
                .jsonPath("$.email").isEqualTo(TEST_EMAIL);
    }

    /**
     * Polls GET /actuator/health every 10 s, up to 15 minutes, until HTTP 200.
     *
     * <p>Replaces the Testcontainers socat-ambassador Wait strategy which is broken
     * with Docker Compose v2 hyphen-based container naming.
     */
    private static void waitForGatewayHealth() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GATEWAY_BASE_URL + "/actuator/health"))
                .GET().build();

        for (int attempt = 0; attempt < 90; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return;
            } catch (Exception ignored) {}
            Thread.sleep(10_000);
        }
        throw new RuntimeException("API Gateway did not become healthy within 15 minutes");
    }
}
