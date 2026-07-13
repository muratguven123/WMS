package com.wms.core.controller;

import com.wms.core.dto.auth.LoginRequest;
import com.wms.core.dto.auth.TokenResponse;
import com.wms.core.service.auth.KeycloakAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> loginJson(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(keycloakAuthService.login(request.username(), request.password()));
    }

    /** Tarayıcı/form istekleri için — JSON parse hatalarını önler. */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> loginForm(
            @RequestParam("username") String username,
            @RequestParam("password") String password) {
        return ResponseEntity.ok(keycloakAuthService.login(username, password));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(keycloakAuthService.refresh(refreshToken));
    }
}
