package com.innowise.apigateway.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_whenNameIsBlank_shouldFail() {
        RegisterRequest request = new RegisterRequest(
                "", "Smith", LocalDate.of(1995, Month.JANUARY, 1), "user@example.com", "password123");

        Set<ConstraintViolation<RegisterRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    void validate_whenAllFieldsAreValid_shouldPass() {
        RegisterRequest request = new RegisterRequest(
                "Alice", "Smith", LocalDate.of(1995, Month.JANUARY, 1), "alice@example.com", "securePass1");

        Set<ConstraintViolation<RegisterRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isEmpty();
    }
}
