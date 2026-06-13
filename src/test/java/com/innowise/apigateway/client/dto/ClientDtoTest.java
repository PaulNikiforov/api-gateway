package com.innowise.apigateway.client.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientDtoTest {

    @Test
    void createUserRequest_exposesFieldsViaAccessors() {
        CreateUserRequest req = new CreateUserRequest("Alice", "alice@example.com");

        assertThat(req.name()).isEqualTo("Alice");
        assertThat(req.email()).isEqualTo("alice@example.com");
    }

    @Test
    void userCreatedResponse_exposesUserIdViaAccessor() {
        UserCreatedResponse resp = new UserCreatedResponse(7L);

        assertThat(resp.userId()).isEqualTo(7L);
    }

    @Test
    void saveCredentialsRequest_exposesFieldsViaAccessors() {
        SaveCredentialsRequest req = new SaveCredentialsRequest(7L, "alice@example.com", "secret");

        assertThat(req.userId()).isEqualTo(7L);
        assertThat(req.email()).isEqualTo("alice@example.com");
        assertThat(req.password()).isEqualTo("secret");
    }

    @Test
    void credentialsResponse_exposesFieldsViaAccessors() {
        CredentialsResponse resp = new CredentialsResponse("access-token", "refresh-token");

        assertThat(resp.accessToken()).isEqualTo("access-token");
        assertThat(resp.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void validationResponse_exposesFieldsViaAccessors() {
        ValidationResponse resp = new ValidationResponse(7L, "USER");

        assertThat(resp.userId()).isEqualTo(7L);
        assertThat(resp.role()).isEqualTo("USER");
    }
}
