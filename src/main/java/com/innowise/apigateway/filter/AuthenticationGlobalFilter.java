package com.innowise.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.apigateway.client.TokenValidationClient;
import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
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
 * Global authentication filter for Spring Cloud Gateway.
 *
 * <p>Intercepts every incoming request and enforces JWT-based authentication,
 * delegating token validation to {@link TokenValidationClient}. Requests
 * matching the whitelist (public endpoints) bypass validation and are forwarded
 * directly to the downstream service.</p>
 *
 * <p>On successful validation the filter propagates {@code X-User-Id} and
 * {@code X-User-Role} headers to downstream services, stripping any
 * client-supplied values of those headers to prevent spoofing.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WHITELIST = Set.of("/api/v1/register", "/api/v1/auth/login", "/api/v1/auth/refresh");
    private static final String BEARER_PREFIX = "Bearer "; // includes trailing space
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final TokenValidationClient validationClient;
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

    /**
     * Mutates the request to add {@code X-User-Id} and {@code X-User-Role} headers
     * (removing any client-supplied values first) and forwards to the filter chain.
     *
     * @param exchange original server web exchange
     * @param chain    gateway filter chain
     * @param vr       validated token response containing user identity
     * @return reactive pipeline forwarding the mutated exchange
     */
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

    /**
     * Writes a 401 Unauthorized JSON response and completes the exchange,
     * preventing further filter chain processing.
     *
     * @param exchange current server web exchange
     * @return reactive pipeline writing the error response
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(Instant.now(), 401, "Unauthorized",
                "Missing or invalid token", path);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
