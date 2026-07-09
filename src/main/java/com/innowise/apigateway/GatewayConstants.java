package com.innowise.apigateway;

import java.time.Duration;

public final class GatewayConstants {

    public static final Duration PER_CALL_TIMEOUT = Duration.ofSeconds(5);

    private GatewayConstants() {}
}
