package com.wms.core.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

final class ControllerTestSecuritySupport {

    private ControllerTestSecuritySupport() {
    }

    static void authenticateAsWmsAdmin() {
        var auth = new UsernamePasswordAuthenticationToken(
                "test-user",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
