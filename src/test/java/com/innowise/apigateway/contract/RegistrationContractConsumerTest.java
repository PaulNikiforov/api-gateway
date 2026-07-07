package com.innowise.apigateway.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCC consumer-side contract test for the registration flow.
 *
 * <p>{@link StubRunnerExtension} downloads the authservice and userservice stub JARs from
 * local Maven ({@code ~/.m2}) and starts a WireMock server per provider with the mappings
 * generated from their contracts. The test then exercises {@link RegistrationService}
 * against those contract-derived stubs.
 *
 * <p>The JUnit extension is used instead of {@code @SpringBootTest @AutoConfigureStubRunner}
 * deliberately: a Spring Boot test context disables reactor-netty's global event-loop/connection
 * resources, which leaves a plain reactive {@link WebClient} without an event loop (the call then
 * hangs). Running without a Spring context keeps those resources active, so the WebClient behaves
 * exactly as in production.
 *
 * <p>The hand-built WebClient is configured with {@link JavaTimeModule} so {@code LocalDate}
 * serializes as an ISO string ({@code 1990-01-01}); the default would emit a numeric array and
 * fail the contract's {@code \d{4}-\d{2}-\d{2}} birthDate matcher.
 *
 * <p>The email must be {@code contract@gateway.com} to satisfy the exact-match
 * constraint in {@code authservice/contracts/api-gateway/should_save_credentials.groovy}.
 */
class RegistrationContractConsumerTest {

    @RegisterExtension
    static StubRunnerExtension stubRunner = new StubRunnerExtension()
            .downloadStub("com.innowise", "authservice", "+", "stubs")
            .downloadStub("com.innowise", "userservice", "+", "stubs")
            .stubsMode(StubRunnerProperties.StubsMode.LOCAL);

    private RegistrationService registrationService;
    private WebClient userClient;
    private WebClient authClient;

    @BeforeEach
    void setUp() {
        int authPort = stubRunner.findStubUrl("com.innowise", "authservice").getPort();
        int userPort = stubRunner.findStubUrl("com.innowise", "userservice").getPort();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                })
                .build();

        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(HttpClient.create());

        userClient = WebClient.builder()
                .clientConnector(connector).exchangeStrategies(strategies)
                .baseUrl("http://localhost:" + userPort).build();
        authClient = WebClient.builder()
                .clientConnector(connector).exchangeStrategies(strategies)
                .baseUrl("http://localhost:" + authPort).build();

        registrationService = new RegistrationService(userClient, authClient);
    }

    @Test
    void register_withValidRequest_receivesTokensFromStubs() {
        RegisterRequest request = new RegisterRequest(
                "Contract", "User", LocalDate.of(1990, 1, 1), "contract@gateway.com", "ContractPass1!");

        StepVerifier.create(registrationService.register(request))
                .assertNext(response -> {
                    assertThat(response.userId()).isPositive();
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.refreshToken()).isNotBlank();
                })
                .verifyComplete();
    }

    /**
     * {@code should_login_user.groovy} is pure Gateway routing in production (no Java code parses
     * the response body), but the stub is downloaded and otherwise never asserted against — this
     * closes that gap directly against the contract-derived stub, independent of routing config.
     */
    @Test
    void login_withValidRequest_receivesTokensFromStub() {
        Map<String, Object> body = authClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", "contract@gateway.com", "password", "ContractPass1!"))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        assertThat(body).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("refreshToken")).isNotNull();
        assertThat(body.get("userId")).isNotNull();
        assertThat(body.get("role")).isNotNull();
    }

    /**
     * Exercises {@code should_deactivate_user.groovy} directly against the stub. In production
     * this call is only reachable through {@link RegistrationService}'s private compensation path
     * (triggered on auth-service 5xx/network failure); calling the stub directly here verifies the
     * contract itself without needing to force that failure path.
     *
     * <p>The consumer-side stub matches the literal path {@code /api/v1/users/1/deactivate} (see
     * the contract's {@code consumer(...)} value) — the id must be exactly {@code 1}.
     */
    @Test
    void deactivateUser_matchesContract() {
        HttpStatus status = userClient.patch()
                .uri("/api/v1/users/{id}/deactivate", 1L)
                .retrieve()
                .toBodilessEntity()
                .map(response -> (HttpStatus) response.getStatusCode())
                .block();

        assertThat(status).isEqualTo(HttpStatus.OK);
    }

    /**
     * Exercises {@code should_delete_inactive_user.groovy} directly against the stub — see
     * {@link #deactivateUser_matchesContract()} for why this bypasses {@link RegistrationService}.
     *
     * <p>The consumer-side stub matches the literal path {@code /api/v1/users/1} — the id must be
     * exactly {@code 1}.
     */
    @Test
    void deleteUser_matchesContract() {
        HttpStatus status = userClient.delete()
                .uri("/api/v1/users/{id}", 1L)
                .retrieve()
                .toBodilessEntity()
                .map(response -> (HttpStatus) response.getStatusCode())
                .block();

        assertThat(status).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
