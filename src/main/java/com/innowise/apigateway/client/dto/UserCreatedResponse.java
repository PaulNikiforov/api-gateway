package com.innowise.apigateway.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserCreatedResponse(@JsonProperty("id") Long userId) {
}
