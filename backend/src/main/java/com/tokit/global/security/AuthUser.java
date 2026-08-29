package com.tokit.global.security;

import com.tokit.domain.user.entity.Role;

/**
 * 인증된 요청 주체. 컨트롤러는 클라이언트가 보낸 userId 대신 이 값을 신뢰합니다.
 */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
