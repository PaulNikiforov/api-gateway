package com.innowise.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.apigateway.client.AuthTokenValidationClient;
import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.dto.ErrorResponse;
import com.innowise.apigateway.exception.InvalidTokenException;
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

/**
 * Global JWT auth filter — delegates validation to Auth Service; injects X-User-Id and X-User-Role on success.
 *
 * <p>Bypasses authentication for:
 * <ul>
 *   <li>OPTIONS requests (CORS preflight) — pass through unconditionally</li>
 *   <li>POST requests to {@link #WHITELIST} paths (register, login, refresh) — no JWT required</li>
 *   <li>Requests whose path starts with any {@link #PUBLIC_PREFIXES} entry (Swagger UI, API docs, webjars)</li>
 * </ul>
 *
 * <p>All other requests must carry {@code Authorization: Bearer <token>}. On validation failure
 * returns {@code 401 Unauthorized} with a JSON {@link ErrorResponse} body.
 *
 * <p>Client-supplied {@code X-User-Id} and {@code X-User-Role} headers are stripped unconditionally
 * at the top of every request — before any bypass or authentication decision — to prevent header injection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WHITELIST = Set.of("/api/v1/register", "/api/v1/auth/login", "/api/v1/auth/refresh");
    private static final Set<String> PUBLIC_PREFIXES = Set.of("/swagger-ui", "/v3/api-docs", "/webjars");
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

        // Strip attacker-controlled identity headers before any routing decision (S1)
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                })
                .build();
        ServerWebExchange strippedExchange = exchange.mutate().request(strippedRequest).build();

        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(strippedExchange);
        }
        if (WHITELIST.contains(path) && HttpMethod.POST.equals(method)) {
            return chain.filter(strippedExchange);
        }
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(strippedExchange);
        }

        String authHeader = strippedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Rejected unauthenticated request: {}", path);
            return unauthorized(strippedExchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        return validationClient.validate(token)
                .flatMap(vr -> forwardWithIdentity(strippedExchange, chain, vr))
                .onErrorResume(InvalidTokenException.class, e -> {
                    log.warn("Token validation failed for path {}: {}", path, e.getMessage());
                    return unauthorized(strippedExchange);
                });
    }

    private Mono<Void> forwardWithIdentity(ServerWebExchange strippedExchange, GatewayFilterChain chain, ValidationResponse vr) {
        if (vr.userId() == null || vr.role() == null) {
            log.warn("Auth Service returned null identity fields for path {}", strippedExchange.getRequest().getPath().value());
            return unauthorized(strippedExchange);
        }
        ServerHttpRequest mutated = strippedExchange.getRequest().mutate()
                .header(HEADER_USER_ID, String.valueOf(vr.userId()))
                .header(HEADER_USER_ROLE, vr.role())
                .build();
        return chain.filter(strippedExchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(Instant.now(), 401, "Unauthorized",
                "Missing or invalid token", path);
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(body))
                .onErrorResume(e -> {
                    log.error("Failed to serialize 401 response for {}", path, e);
                    return Mono.empty();
                })
                .map(bytes -> response.bufferFactory().wrap(bytes))
                .flatMap(buffer -> response.writeWith(Mono.just(buffer)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
