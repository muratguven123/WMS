package com.wms.finance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaxRateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTaxRateNotFound(TaxRateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Tax Rate Not Found", ex.getMessage());
    }

    @ExceptionHandler(TaxRateConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTaxRateConflict(TaxRateConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Tax Rate Conflict", ex.getMessage());
    }

    @ExceptionHandler(TaxResolutionException.class)
    public ResponseEntity<Map<String, Object>> handleTaxResolution(TaxResolutionException ex) {
        return problem(HttpStatus.NOT_FOUND, "Tax Rate Not Found", ex.getMessage());
    }

    @ExceptionHandler(ExchangeRateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleExchangeRateNotFound(ExchangeRateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Exchange Rate Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidCustomerCurrencyException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCustomerCurrency(
            InvalidCustomerCurrencyException ex) {

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Invalid Currency Permission",
                "message", ex.getMessage(),
                "details", Map.of(
                        "customerId", ex.getCustomerId().toString(),
                        "currencyId", ex.getCurrencyId().toString()
                )
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", ex.getMessage()
        );

        return ResponseEntity.badRequest().body(body);
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
