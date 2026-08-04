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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "services.auth-service-url=http://localhost:9999",
        "services.user-service-url=http://localhost:9998",
        "services.order-service-url=http://localhost:9997",
        "services.payment-service-url=http://localhost:9996"
})
class RouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void getRoutes_whenContextLoaded_shouldRouteAuthUserOrderAndPaymentServicesFromServicesProperties() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "auth-service".equals(r.getId()) || "user-service".equals(r.getId()) || "order-service".equals(r.getId()) || "payment-service".equals(r.getId()))
                        .collectMap(Route::getId, r -> r.getUri().toString())
        )
                .assertNext(routes -> {
                    assertThat(routes).containsKeys("auth-service", "user-service", "order-service", "payment-service");
                    assertThat(routes).containsEntry("auth-service", "http://localhost:9999");
                    assertThat(routes).containsEntry("user-service", "http://localhost:9998");
                    assertThat(routes).containsEntry("order-service", "http://localhost:9997");
                    assertThat(routes).containsEntry("payment-service", "http://localhost:9996");
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
    void getRoutes_whenContextLoaded_shouldMatchOrderPathPredicateAndRejectOtherPaths() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "order-service".equals(r.getId()))
                        .next()
        )
                .assertNext(route -> {
                    assertThat(matches(route, "/api/v1/orders/123")).isTrue();
                    assertThat(matches(route, "/api/v1/users/1")).isFalse();
                    assertThat(matches(route, "/api/v1/register")).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void getRoutes_whenContextLoaded_shouldMatchPaymentPathPredicateAndRejectOtherPaths() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "payment-service".equals(r.getId()))
                        .next()
        )
                .assertNext(route -> {
                    assertThat(matches(route, "/api/v1/payments/55")).isTrue();
                    assertThat(matches(route, "/api/v1/users/1")).isFalse();
                    assertThat(matches(route, "/api/v1/register")).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void getRoutes_whenContextLoaded_shouldMatchCardsPathPredicateAndRouteToUserServiceUrl() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .filter(r -> "user-cards".equals(r.getId()))
                        .next()
        )
                .assertNext(route -> {
                    assertThat(route.getUri()).hasToString("http://localhost:9998");
                    assertThat(matches(route, "/api/v1/cards/42")).isTrue();
                    assertThat(matches(route, "/api/v1/users/1")).isFalse();
                    assertThat(matches(route, "/api/v1/register")).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void getRoutes_whenContextLoaded_shouldHaveExactlyFiveRoutesIncludingUserCards() {
        StepVerifier.create(
                routeLocator.getRoutes()
                        .map(Route::getId)
                        .collectList()
        )
                .assertNext(ids -> {
                    assertThat(ids).containsExactlyInAnyOrder("auth-service", "user-service", "order-service", "payment-service", "user-cards");
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
