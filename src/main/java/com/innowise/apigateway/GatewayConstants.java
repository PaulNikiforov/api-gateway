package com.innowise.apigateway;

import java.time.Duration;

public final class GatewayConstants {

    public static final Duration PER_CALL_TIMEOUT = Duration.ofSeconds(10);

    public static final long DOWNSTREAM_RETRIES = 1;

    public static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);

    public static final Duration COMPENSATION_TIMEOUT =
            PER_CALL_TIMEOUT.multipliedBy(2).multipliedBy(1 + DOWNSTREAM_RETRIES);

    private GatewayConstants() {}
}
