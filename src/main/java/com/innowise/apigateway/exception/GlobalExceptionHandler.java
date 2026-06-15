package com.innowise.apigateway.exception;

import com.innowise.apigateway.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

/**
 * Global exception handler for the API Gateway.
 *
 * <p>Intercepts exceptions thrown by {@code @RestController} methods (e.g. registration)
 * and maps them to a uniform {@link ErrorResponse} envelope. Exceptions from the Gateway
 * filter chain and routing path bypass this handler and are caught by
 * {@link GatewayErrorWebExceptionHandler} instead.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles bean-validation failures raised by {@code @Valid} on request bodies.
     *
     * <p>Extracts the default message from the first field error and wraps it in
     * an {@link ErrorResponse} with HTTP 400 Bad Request.
     *
     * @param ex       the binding exception produced by WebFlux validation
     * @param exchange the current server exchange (used to obtain the request path)
     * @return a 400 response containing a populated {@link ErrorResponse}
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            WebExchangeBindException ex,
            ServerWebExchange exchange) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse(ex.getReason());

        String path = exchange.getRequest().getPath().value();

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                path
        );

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Forwards the HTTP status received from a downstream service to the caller.
     * Preserves 4xx status codes (e.g. 409 duplicate email) and maps 5xx/connection
     * errors to 502 Bad Gateway.
     *
     * @param ex       the exception carrying the downstream HTTP status and body
     * @param exchange the current server exchange
     * @return a response with the downstream status and a populated {@link ErrorResponse}
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleDownstream(
            WebClientResponseException ex,
            ServerWebExchange exchange) {

        HttpStatusCode status = ex.getStatusCode();
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                ex.getStatusText(),
                ex.getMessage(),
                path
        );
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Catch-all handler for unexpected exceptions; returns 500 Internal Server Error.
     *
     * @param ex       any unhandled exception
     * @param exchange the current server exchange
     * @return a 500 response with a populated {@link ErrorResponse}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(
            Exception ex,
            ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                path
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
