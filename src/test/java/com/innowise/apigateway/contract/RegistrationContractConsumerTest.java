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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.time.LocalDate;

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

        WebClient userClient = WebClient.builder()
                .clientConnector(connector).exchangeStrategies(strategies)
                .baseUrl("http://localhost:" + userPort).build();
        WebClient authClient = WebClient.builder()
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
}
