package com.innowise.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.apigateway.client.AuthTokenValidationClient;
import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

/** Global JWT auth filter — delegates validation to Auth Service; injects X-User-Id and X-User-Role on success. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WHITELIST = Set.of("/api/v1/register", "/api/v1/auth/login", "/api/v1/auth/refresh");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final AuthTokenValidationClient validationClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        log.debug("Processing request: method={} path={}", method, path);

        if (WHITELIST.contains(path) && HttpMethod.POST.equals(method)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Rejected unauthenticated request: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        return validationClient.validate(token)
                .flatMap(vr -> forwardWithIdentity(exchange, chain, vr))
                .onErrorResume(e -> {
                    log.warn("Token validation failed for path {}: {}", path, e.getMessage());
                    return unauthorized(exchange);
                });
    }

    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange, GatewayFilterChain chain, ValidationResponse vr) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                })
                .header(HEADER_USER_ID, String.valueOf(vr.userId()))
                .header(HEADER_USER_ROLE, vr.role())
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(Instant.now(), 401, "Unauthorized",
                "Missing or invalid token", path);
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(body))
                .map(bytes -> response.bufferFactory().wrap(bytes))
                .flatMap(buffer -> response.writeWith(Mono.just(buffer)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
