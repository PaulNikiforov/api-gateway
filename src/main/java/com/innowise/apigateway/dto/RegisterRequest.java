// FILE: src/main/java/com/innowise/apigateway/dto/RegisterRequest.java
package com.innowise.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration requests.
 *
 * @param name     the user's display name
 * @param email    the user's email address
 * @param password the user's password (minimum 8 characters)
 */
public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}
