package com.innowise.apigateway.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response from User Service POST /users. User Service returns field "id"; mapped to userId. */
public record UserCreatedResponse(@JsonProperty("id") Long userId) {
}
