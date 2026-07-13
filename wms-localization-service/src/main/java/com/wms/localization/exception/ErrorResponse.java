package com.wms.localization.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        OffsetDateTime timestamp
) {

    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, null, message, path, OffsetDateTime.now());
    }

    public ErrorResponse(int status, String error, String errorCode, String message, String path) {
        this(status, error, errorCode, message, path, OffsetDateTime.now());
    }
}
