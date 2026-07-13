package com.wms.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Küresel exception handler — tüm controller'lardan fırlatılan exception'ları yakalar
 * ve standart {@link ErrorResponse} formatında JSON yanıt döner.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("BusinessException: {} [status={}, errorCode={}]",
                ex.getMessage(), ex.getStatus(), ex.getErrorCode());

        ErrorResponse body = new ErrorResponse(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * requiresApproval = true olan adımlar tetiklendiğinde → HTTP 202 Accepted.
     * İşlem hatalı değil; onay bekleniyor.
     */
    @ExceptionHandler(ApprovalRequiredException.class)
    public ResponseEntity<ErrorResponse> handleApprovalRequired(
            ApprovalRequiredException ex, HttpServletRequest request) {

        log.info("ApprovalRequiredException: approvalRequestId={}", ex.getApprovalRequestId());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.ACCEPTED.value(),
                "Accepted",
                "APPROVAL_REQUIRED",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {

        log.error("SecurityException: {}", ex.getMessage());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "SECURITY_VIOLATION",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Dinamik form validasyon hatası — alan bazında detaylı hata listesi döner.
     *
     * <p>Response body yapısı:</p>
     * <pre>
     * {
     *   "status": 400,
     *   "error": "Bad Request",
     *   "errorCode": "DYNAMIC_FORM_VALIDATION_FAILED",
     *   "message": "Dinamik form validasyonu başarısız. screenCode=REC_CONTROL_FORM, hata sayısı=2",
     *   "path": "/api/receipts",
     *   "screenCode": "REC_CONTROL_FORM",
     *   "fieldErrors": [
     *     { "fieldKey": "tax_number", "errorCode": "MANDATORY_FIELD_MISSING",  "messageKey": "validation.tax_number.required",   "rejectedValue": null    },
     *     { "fieldKey": "zip_code",   "errorCode": "REGEX_VALIDATION_FAILED",  "messageKey": "validation.zip_code.invalid",      "rejectedValue": "ABC"   }
     *   ]
     * }
     * </pre>
     */
    @ExceptionHandler(DynamicValidationException.class)
    public ResponseEntity<DynamicValidationErrorResponse> handleDynamicValidation(
            DynamicValidationException ex, HttpServletRequest request) {

        log.warn("[DynamicValidation] {} alan hatası. screenCode={} path={}",
                ex.getFieldErrors().size(), ex.getScreenCode(), request.getRequestURI());

        var body = new DynamicValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "DYNAMIC_FORM_VALIDATION_FAILED",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getScreenCode(),
                ex.getFieldErrors()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "VALIDATION_ERROR",
                message,
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at {}", request.getRequestURI(), ex);

        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
