package com.innowise.apigateway.filter;

import com.innowise.apigateway.client.AuthTokenValidationClient;
import com.innowise.apigateway.client.dto.ValidationResponse;
import com.innowise.apigateway.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Global JWT auth filter — delegates validation to Auth Service; injects X-User-Id and X-User-Role on success.
 *
 * <p>Bypasses authentication for:
 * <ul>
 *   <li>OPTIONS requests (CORS preflight) — pass through unconditionally</li>
 *   <li>POST requests to {@link #WHITELIST} paths (register, login, refresh) — no JWT required</li>
 *   <li>Requests whose path starts with a Swagger UI, API docs, or webjars prefix</li>
 * </ul>
 *
 * <p>All other requests must carry {@code Authorization: Bearer <token>}. On validation failure
 * signals {@link ResponseStatusException} with 401, which is rendered uniformly by
 * {@link com.innowise.apigateway.exception.GatewayErrorWebExceptionHandler}.
 *
 * <p>Client-supplied {@code X-User-Id} and {@code X-User-Role} headers are stripped unconditionally
 * at the top of every request — before any bypass or authentication decision — to prevent header injection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> WHITELIST = Set.of("/api/v1/register", "/api/v1/auth/login", "/api/v1/auth/refresh");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final AuthTokenValidationClient validationClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getPath().value();
        // Normalize trailing slash so /api/v1/register/ is treated the same as /api/v1/register (F1)
        String path = rawPath.length() > 1 && rawPath.endsWith("/")
                ? rawPath.substring(0, rawPath.length() - 1)
                : rawPath;
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
        // Inline prefix check avoids per-request Stream allocation (F9)
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) {
            return chain.filter(strippedExchange);
        }

        String authHeader = strippedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Rejected unauthenticated request: {}", path);
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token"));
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        return validationClient.validate(token)
                .flatMap(vr -> forwardWithIdentity(strippedExchange, chain, vr))
                .onErrorResume(InvalidTokenException.class, e -> {
                    log.warn("Token validation failed for path {}: {}", path, e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token"));
                });
    }

    private Mono<Void> forwardWithIdentity(ServerWebExchange strippedExchange, GatewayFilterChain chain, ValidationResponse vr) {
        if (vr.userId() == null || vr.role() == null) {
            log.warn("Auth Service returned null identity fields for path {}", strippedExchange.getRequest().getPath().value());
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token"));
        }
        ServerHttpRequest mutated = strippedExchange.getRequest().mutate()
                .header(HEADER_USER_ID, String.valueOf(vr.userId()))
                .header(HEADER_USER_ROLE, vr.role())
                .build();
        return chain.filter(strippedExchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
