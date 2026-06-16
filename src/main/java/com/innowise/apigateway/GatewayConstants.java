package com.innowise.apigateway;

import java.time.Duration;

public final class GatewayConstants {

    /** Per-hop timeout applied to every individual downstream WebClient call. */
    public static final Duration PER_CALL_TIMEOUT = Duration.ofSeconds(5);

    private GatewayConstants() {}
}
