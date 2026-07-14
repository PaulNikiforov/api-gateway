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
import java.time.Month;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                "Contract", "User", LocalDate.of(1990, Month.JANUARY, 1), "contract@gateway.com", "ContractPass1!");

        StepVerifier.create(registrationService.register(request))
                .assertNext(response -> {
                    assertThat(response.userId()).isPositive();
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.refreshToken()).isNotBlank();
                })
                .verifyComplete();
    }

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
