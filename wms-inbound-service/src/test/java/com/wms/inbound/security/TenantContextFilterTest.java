package com.wms.inbound.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

  @Test
  @DisplayName("Sayısal wms_user_id claim parse edilir")
  void extractWmsUserId_numericClaim() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("wms_user_id", "1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));

    assertThat(TenantContextFilter.extractWmsUserId(auth)).isEqualTo(1L);
  }

  @Test
  @DisplayName("Demo kullanıcı legacy UUID claim'i sayısal ID'ye eşlenir")
  void extractWmsUserId_legacyDemoUuidClaim() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("wms_user_id", "55555555-0000-0000-0000-000000000001")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));

    assertThat(TenantContextFilter.extractWmsUserId(auth)).isEqualTo(1L);
  }

  @Test
  @DisplayName("Bilinmeyen UUID wms_user_id claim reddedilir")
  void extractWmsUserId_unknownUuidClaim_returnsNull() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("wms_user_id", "55555555-5555-5555-5555-555555555555")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));

    assertThat(TenantContextFilter.extractWmsUserId(auth)).isNull();
  }

  @Test
  @DisplayName("Eksik wms_user_id claim reddedilir")
  void extractWmsUserId_missingClaim_returnsNull() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));

    assertThat(TenantContextFilter.extractWmsUserId(auth)).isNull();
  }
}
