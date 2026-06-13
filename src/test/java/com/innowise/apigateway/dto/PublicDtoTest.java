package com.innowise.apigateway.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDtoTest {

    @Test
    void registerResponse_exposesFieldsViaAccessors() {
        RegisterResponse response = new RegisterResponse(42L, "access-token", "refresh-token");

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void errorResponse_exposesFieldsViaAccessors() {
        Instant now = Instant.now();
        ErrorResponse error = new ErrorResponse(now, 404, "Not Found", "Resource missing", "/api/resource");

        assertThat(error.timestamp()).isEqualTo(now);
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.error()).isEqualTo("Not Found");
        assertThat(error.message()).isEqualTo("Resource missing");
        assertThat(error.path()).isEqualTo("/api/resource");
    }
}
