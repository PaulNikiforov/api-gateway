package com.innowise.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String surname,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {
}
