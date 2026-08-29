package com.tokit.domain.user.controller;

import com.tokit.global.security.AuthUser;
import com.tokit.domain.user.dto.MyPageResponse;
import com.tokit.domain.user.service.MyPageService;
import com.tokit.global.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MyPageControllerTest {

    @Mock
    private MyPageService myPageService;

    @InjectMocks
    private MyPageController myPageController;

    @Test
    @DisplayName("getMyPage: 사용자 마이페이지 통합 조회 시 UserProfile, 지갑, 주문, 체결 내역이 담긴 ApiResponse가 반환된다.")
    void getMyPage_ReturnsSuccessResponse() {
        // Given
        Long userId = 1L;
        UserController.UserResponse userResponse = new UserController.UserResponse(
                1L, "juhee@tokit.com", "Juhee", "0xWALLET_ADDR", true
        );
        MyPageResponse myPageResponse = new MyPageResponse(
                userResponse,
                List.of(),
                List.of(),
                List.of()
        );

        given(myPageService.getMyPageData(userId)).willReturn(myPageResponse);

        // When
        ResponseEntity<ApiResponse<MyPageResponse>> response = myPageController.getMyPage(new AuthUser(userId, "juhee@tokit.com", com.tokit.domain.user.entity.Role.USER));

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(200);
        assertThat(response.getBody().message()).isEqualTo("SUCCESS");
        assertThat(response.getBody().data().user().name()).isEqualTo("Juhee");
        assertThat(response.getBody().data().user().email()).isEqualTo("juhee@tokit.com");
    }
}
