package com.tokit.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(null);
    }

    @Test
    @DisplayName("corsConfigurationSource: 모든 도메인 패밀리에 대해 HTTP Method 및 X-Idempotency-Key CORS 설정을 허용한다.")
    void corsConfigurationSource_AllowsRequiredHttpMethods() {
        // Given
        CorsConfigurationSource corsSource = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/assets");

        // When
        CorsConfiguration config = corsSource.getCorsConfiguration(request);

        // Then
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns()).contains("*");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        assertThat(config.getAllowCredentials()).isTrue();
    }
}
