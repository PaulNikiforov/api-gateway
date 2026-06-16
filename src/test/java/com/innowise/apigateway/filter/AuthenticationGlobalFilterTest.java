package com.innowise.apigateway.filter;

import com.innowise.apigateway.client.AuthTokenValidationClient;
import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationGlobalFilterTest {

    @Mock
    private AuthTokenValidationClient validationClient;

    @Mock
    private GatewayFilterChain chain;

    private AuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationGlobalFilter(validationClient);
    }

    @Test
    void filter_whenWhitelistedEndpoint_shouldSkipValidationAndCallChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(validationClient, never()).validate(any());
        verify(chain).filter(any());
    }

    @Test
    void filter_whenWhitelistedEndpointWithTrailingSlash_shouldSkipValidationAndCallChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login/").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(validationClient, never()).validate(any());
        verify(chain).filter(any());
    }

    @Test
    void filter_whenProtectedEndpointWithoutAuthHeader_shouldSignal401AndNotCallChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("Missing or invalid token");
                })
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_whenValidToken_shouldForwardWithXUserIdAndXUserRoleHeaders() {
        when(validationClient.validate("valid-token"))
                .thenReturn(Mono.just(new ValidationResponse(42L, "USER")));
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer valid-token")
                        .build());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void filter_whenInvalidToken_shouldSignal401AndNotCallChain() {
        when(validationClient.validate(anyString()))
                .thenReturn(Mono.error(new InvalidTokenException()));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer bad-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_whenWhitelistedEndpointWithInjectedHeaders_shouldStripBeforeForwarding() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/register")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .build());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
    }

    @Test
    void filter_whenOptionsMethod_shouldBypassAuthAndCallChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(validationClient, never()).validate(any());
        verify(chain).filter(any());
    }

    @Test
    void filter_whenAuthServiceUnreachable_shouldPropagateErrorNotReturn401() {
        when(validationClient.validate(anyString()))
                .thenReturn(Mono.error(new ConnectException("Connection refused")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer some-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(ConnectException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_whenAuthServiceReturnsNullUserId_shouldSignal401() {
        when(validationClient.validate(anyString()))
                .thenReturn(Mono.just(new ValidationResponse(null, null)));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer some-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_whenClientSendsXUserId_shouldOverwriteWithValidatedUserId() {
        when(validationClient.validate(anyString()))
                .thenReturn(Mono.just(new ValidationResponse(42L, "USER")));
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users")
                        .header("Authorization", "Bearer valid-token")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .build());

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }
}
