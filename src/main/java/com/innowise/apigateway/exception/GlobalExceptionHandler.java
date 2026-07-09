package com.innowise.apigateway.exception;

import com.innowise.apigateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleDownstream(
            WebClientResponseException ex,
            ServerWebExchange exchange) {

        HttpStatusCode rawStatus = ex.getStatusCode();
        HttpStatusCode gatewayStatus = rawStatus.is5xxServerError() ? HttpStatus.BAD_GATEWAY : rawStatus;
        HttpStatus resolved = HttpStatus.resolve(gatewayStatus.value());
        String error = resolved != null ? resolved.getReasonPhrase() : ex.getStatusText();
        String message = rawStatus.is5xxServerError() ? "Upstream service error"
                : (resolved != null ? resolved.getReasonPhrase() : "Client error");
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                gatewayStatus.value(),
                error,
                message,
                path
        );
        return ResponseEntity.status(gatewayStatus).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(
            Exception ex,
            ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        log.error("Unhandled exception for path {}", path, ex);
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An internal error occurred",
                path
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
