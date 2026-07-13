package com.wms.core.dto.auth;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String idToken,
        long expiresIn,
        String tokenType
) {}
