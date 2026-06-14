package com.innowise.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Routes are profile-specific; @ActiveProfiles("local") loads application-local.yml.
// @TestPropertySource overrides services.* to sentinel values so URI assertions
// prove that routes reference ${services.*} rather than ${AUTH_SERVICE_URL:...} directly.
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "services.auth-service-url=http://localhost:9999",
        "services.user-service-url=http://localhost:9998"
})
class RouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void getRoutes_whenContextLoaded_shouldRouteAuthAndUserServicesFromServicesProperties() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "auth-service".equals(r.getId()) || "user-service".equals(r.getId()))
                        .collectMap(Route::getId, r -> r.getUri().toString())
        )
                .assertNext(routes -> {
                    assertThat(routes).containsKeys("auth-service", "user-service");
                    assertThat(routes.get("auth-service")).isEqualTo("http://localhost:9999");
                    assertThat(routes.get("user-service")).isEqualTo("http://localhost:9998");
                })
                .verifyComplete();
    }

    @Test
    void getRoutes_whenContextLoaded_shouldMatchAuthPathPredicateAndRejectOtherPaths() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "auth-service".equals(r.getId()))
                        .next()
        )
                .assertNext(route -> {
                    assertThat(matches(route, "/api/v1/auth/login")).isTrue();
                    assertThat(matches(route, "/api/v1/auth/refresh")).isTrue();
                    assertThat(matches(route, "/api/v1/users/1")).isFalse();
                    assertThat(matches(route, "/api/v1/register")).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void getRoutes_whenContextLoaded_shouldHaveExactlyAuthAndUserRoutes() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .map(Route::getId)
                        .collectList()
        )
                .assertNext(ids -> {
                    assertThat(ids).containsExactlyInAnyOrder("auth-service", "user-service");
                    assertThat(ids).doesNotContain("order-service", "payment-service");
                })
                .verifyComplete();
    }

    private boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());
        Boolean result = Mono.from(route.getPredicate().apply(exchange)).block();
        return Boolean.TRUE.equals(result);
    }
}
