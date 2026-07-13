package com.wms.localization.exception.address;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Adres şablonu admin işlemlerinde iş kuralı ihlali.
 */
@Getter
public class TemplateConfigException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public TemplateConfigException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
