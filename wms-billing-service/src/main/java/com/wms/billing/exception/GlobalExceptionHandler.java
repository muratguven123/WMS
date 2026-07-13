package com.wms.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleInvoiceNotFound(InvoiceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Invoice Not Found", ex.getMessage());
    }

    @ExceptionHandler(ExchangeRateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleExchangeRateNotFound(ExchangeRateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Exchange Rate Not Found", ex.getMessage());
    }

    @ExceptionHandler(RateLockViolationException.class)
    public ResponseEntity<Map<String, Object>> handleRateLockViolation(RateLockViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Rate Lock Violation", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation Failed", message);
    }

    private static ResponseEntity<Map<String, Object>> problem(
            HttpStatus status, String error, String message) {

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", error,
                "message", message
        );
        return ResponseEntity.status(status).body(body);
    }
}
