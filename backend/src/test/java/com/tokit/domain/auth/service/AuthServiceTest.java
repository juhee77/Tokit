package com.tokit.domain.auth.service;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private User investor;

    @BeforeEach
    void setUp() {
        investor = User.builder()
                .name("Auth Investor")
                .email("investor@tokit.com")
                .password("$2a$10$storedHash")
                .walletAddress("0xAUTH_INVESTOR_ADDRESS")
                .build();
        ReflectionTestUtils.setField(investor, "id", 7L);
    }

    @Test
    @DisplayName("login: 이메일과 비밀번호가 일치하면 해당 사용자로 발급된 액세스 토큰을 반환한다.")
    void login_WithValidCredentials_ReturnsToken() {
        when(userRepository.findByEmail("investor@tokit.com")).thenReturn(Optional.of(investor));
        when(passwordEncoder.matches("tokit1234", "$2a$10$storedHash")).thenReturn(true);
        when(jwtTokenProvider.createToken(7L, "investor@tokit.com")).thenReturn("issued.jwt.token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthService.LoginResult result = authService.login("investor@tokit.com", "tokit1234");

        assertThat(result.accessToken()).isEqualTo("issued.jwt.token");
        assertThat(result.user().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("login: 비밀번호가 틀리면 토큰을 발급하지 않고 인증 실패 예외를 던진다.")
    void login_WithWrongPassword_Throws() {
        when(userRepository.findByEmail("investor@tokit.com")).thenReturn(Optional.of(investor));
        when(passwordEncoder.matches("wrong-password", "$2a$10$storedHash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("investor@tokit.com", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(jwtTokenProvider, never()).createToken(any(), any());
    }

    @Test
    @DisplayName("login: 존재하지 않는 이메일도 비밀번호 불일치와 동일한 예외로 응답해 계정 존재 여부를 노출하지 않는다.")
    void login_WithUnknownEmail_ThrowsSameError() {
        when(userRepository.findByEmail("ghost@tokit.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@tokit.com", "tokit1234"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(jwtTokenProvider, never()).createToken(any(), any());
    }
}
