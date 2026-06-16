package com.innowise.apigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.innowise.apigateway.service.RegistrationService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractDownstreamClientTest {

    protected static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeAll
    static void warmUpNetty() throws IOException {
        try (MockWebServer warmup = new MockWebServer()) {
            warmup.start();
            warmup.enqueue(new MockResponse().setResponseCode(200));
            StepVerifier.create(
                    WebClient.builder().baseUrl(warmup.url("/").toString()).build()
                            .get().retrieve().toBodilessEntity()
            ).expectNextCount(1).expectComplete().verify(Duration.ofSeconds(30));
        }
    }

    protected MockWebServer userServiceServer;
    protected MockWebServer authServiceServer;
    protected RegistrationService registrationService;

    @BeforeEach
    void setUp() throws IOException {
        userServiceServer = new MockWebServer();
        authServiceServer = new MockWebServer();
        userServiceServer.start();
        authServiceServer.start();

        WebClient userClient = WebClient.builder().baseUrl(userServiceServer.url("/").toString()).build();
        WebClient authClient = WebClient.builder().baseUrl(authServiceServer.url("/").toString()).build();
        registrationService = new RegistrationService(userClient, authClient);
        onSetUp(userClient, authClient);
    }

    /** Override to perform additional per-test setup with the same WebClient instances. */
    protected void onSetUp(WebClient userClient, WebClient authClient) {}

    @AfterEach
    void tearDown() throws IOException {
        userServiceServer.shutdown();
        authServiceServer.shutdown();
    }

    protected RecordedRequest takeNext(MockWebServer server) throws InterruptedException {
        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(req).as("expected a request but none arrived within 1 s").isNotNull();
        return req;
    }
}
