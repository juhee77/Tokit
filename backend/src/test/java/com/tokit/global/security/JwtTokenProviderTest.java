package com.tokit.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 3600000L);
    }

    @Test
    @DisplayName("createToken/parseUserId: 발급한 토큰에서 사용자 ID를 원본 그대로 복원한다.")
    void createAndParseRoundTrip() {
        String token = provider.createToken(42L, "investor@tokit.com");

        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("isValid: 다른 시크릿으로 서명된 토큰은 거부한다.")
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider attacker = new JwtTokenProvider(
                "another-secret-key-that-is-also-long-enough-for-hmac-sha256", 3600000L);
        String forged = attacker.createToken(1L, "attacker@tokit.com");

        assertThat(provider.isValid(forged)).isFalse();
    }

    @Test
    @DisplayName("isValid: 만료된 토큰은 거부한다.")
    void rejectsExpiredToken() {
        JwtTokenProvider expiring = new JwtTokenProvider(SECRET, -1000L);
        String expired = expiring.createToken(1L, "investor@tokit.com");

        assertThat(provider.isValid(expired)).isFalse();
    }

    @Test
    @DisplayName("isValid: 변조되었거나 형식이 잘못된 토큰은 거부한다.")
    void rejectsMalformedToken() {
        assertThat(provider.isValid("not-a-jwt")).isFalse();
        assertThat(provider.isValid("")).isFalse();
    }
}
