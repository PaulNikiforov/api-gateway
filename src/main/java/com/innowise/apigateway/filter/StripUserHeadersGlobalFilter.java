package com.innowise.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strips client-supplied {@code X-User-Id} and {@code X-User-Role} headers before a request
 * reaches routing or any downstream service — regardless of whitelist status, HTTP method, or
 * authentication outcome. Prevents header-injection spoofing of identity claims that downstream
 * services might otherwise trust.
 *
 * <p>Runs at the Gateway-routing stage, i.e. after Spring Security's {@code SecurityWebFilterChain}
 * ({@code WebFilter}s execute earlier in the WebFlux pipeline than {@link GlobalFilter}s). This is
 * safe today because JWT authentication only reads the {@code Authorization} header and never
 * trusts {@code X-User-*}; the stripping guarantee is "downstream never sees client-supplied
 * X-User-* headers", not "stripped before Security runs".
 */
@Component
public class StripUserHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                })
                .build();
        return chain.filter(exchange.mutate().request(strippedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
