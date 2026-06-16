package com.innowise.apigateway.controller;

import com.innowise.apigateway.dto.ErrorResponse;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.dto.RegisterResponse;
import com.innowise.apigateway.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Handles {@code POST /api/v1/register}. Not proxied — served directly by Gateway.
 * Listed in public whitelist; no JWT required.
 */
@Tag(name = "Registration", description = "User registration — orchestrated by Gateway across User Service and Auth Service")
@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * Orchestrates user registration: step 1 creates a user profile in User Service
     * ({@code POST /api/v1/users}), step 2 saves credentials in Auth Service
     * ({@code POST /api/v1/auth/credentials}). On Auth Service 5xx/network failure
     * a two-step compensating rollback is fired in the background (PATCH deactivate, then DELETE)
     * and the error is returned to the caller immediately.
     * Returns 201 with {@link RegisterResponse} on success.
     * Possible responses: 400 (validation), 409 (duplicate credentials), 5xx (downstream failure).
     */
    @Operation(
            summary = "Register a new user",
            description = """
                    Two-step orchestration: creates user in User Service, then saves credentials in Auth Service. \
                    On Auth Service failure a compensating DELETE is issued to roll back the user record.""",
            operationId = "register",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (missing fields, password too short)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error or compensation failure",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/v1/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
