package com.tokit.global.security;

import com.tokit.domain.user.entity.Role;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserRoleTest {

    @Test
    @DisplayName("AuthUser.isAdmin: ADMIN 권한 주체만 관리자로 인식된다.")
    void isAdmin_OnlyForAdminRole() {
        assertThat(new AuthUser(1L, "admin@tokit.com", Role.ADMIN).isAdmin()).isTrue();
        assertThat(new AuthUser(2L, "investor@tokit.com", Role.USER).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("User: 권한을 지정하지 않고 가입하면 일반 사용자(USER)로 생성된다.")
    void user_DefaultsToUserRole() {
        User user = User.builder()
                .name("New Investor")
                .email("new@tokit.com")
                .password("$2a$10$hash")
                .walletAddress("0xNEW_INVESTOR")
                .build();

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("User: 운영자 계정은 ADMIN 권한으로 생성된다.")
    void user_AdminRoleIsPreserved() {
        User admin = User.builder()
                .name("Operator")
                .email("ops@tokit.com")
                .password("$2a$10$hash")
                .walletAddress("0xOPERATOR")
                .role(Role.ADMIN)
                .build();

        assertThat(admin.isAdmin()).isTrue();
    }
}
