package com.innowise.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO for user registration requests.
 *
 * @param name      the user's first name
 * @param surname   the user's last name
 * @param birthDate the user's date of birth (must be in the past)
 * @param email     the user's email address
 * @param password  the user's password (minimum 8 characters)
 */
public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String surname,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}
