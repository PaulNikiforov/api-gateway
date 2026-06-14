package com.innowise.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// webEnvironment = NONE is incompatible with GatewayAutoConfiguration (requires ServerProperties);
// full context is the minimal viable option for this smoke test.
@SpringBootTest
@TestPropertySource(properties = {
        "services.auth-service-url=http://localhost:9999",
        "services.user-service-url=http://localhost:9998"
})
class WebClientConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void getBeansOfType_whenContextLoaded_shouldExposeExactlyTwoWebClientBeans() {
        Map<String, WebClient> beans = context.getBeansOfType(WebClient.class);
        assertThat(beans)
                .hasSize(2)
                .containsKeys("authServiceWebClient", "userServiceWebClient");
    }
}
