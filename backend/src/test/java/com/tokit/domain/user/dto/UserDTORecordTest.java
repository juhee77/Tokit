package com.tokit.domain.user.dto;

import com.tokit.domain.user.controller.UserController.*;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDTORecordTest {

    @Test
    @DisplayName("SignUpRequest DTO 레코드 검증: 회원가입 요청 이메일, 이름, 지갑 주소가 정확히 캡슐화된다.")
    void signUpRequest_InstantiationAndAccessors() {
        // Given & When
        SignUpRequest request = new SignUpRequest("user@tokit.com", "Juhee", "0xWALLET_ADDRESS");

        // Then
        assertThat(request.email()).isEqualTo("user@tokit.com");
        assertThat(request.name()).isEqualTo("Juhee");
        assertThat(request.walletAddress()).isEqualTo("0xWALLET_ADDRESS");
    }

    @Test
    @DisplayName("UserResponse 정적 팩토리 메서드 검증: User 엔티티로부터 ID, 이메일, 이름, 지갑주소, KYC상태가 정상 매핑된다.")
    void userResponse_FromFactoryMethod() {
        // Given
        User user = User.builder()
                .email("test@tokit.com")
                .name("TestUser")
                .walletAddress("0xMY_ADDRESS")
                .kycStatus(true)
                .build();
        setField(user, "id", 7L);

        // When
        UserResponse response = UserResponse.from(user);

        // Then
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("test@tokit.com");
        assertThat(response.name()).isEqualTo("TestUser");
        assertThat(response.walletAddress()).isEqualTo("0xMY_ADDRESS");
        assertThat(response.kycStatus()).isTrue();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
