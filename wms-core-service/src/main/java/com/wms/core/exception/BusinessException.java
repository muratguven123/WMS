package com.wms.core.exception;

import org.springframework.http.HttpStatus;

/**
 * İş kuralı ihlallerinde fırlatılan uygulama seviyesi exception.
 *
 * <p>{@link GlobalExceptionHandler} tarafından yakalanıp
 * ilgili HTTP status kodu ile JSON response'a dönüştürülür.</p>
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST, null);
    }

    public BusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public BusinessException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
