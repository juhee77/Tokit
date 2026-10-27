package com.tokit.domain.user.controller;

import com.tokit.support.TestAuthPrincipalResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.user.entity.Role;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.service.UserService;
import com.tokit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private User testUser;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new TestAuthPrincipalResolver(1L, "order.user@tokit.com"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUser = User.builder()
                .name("Controller Investor")
                .email("controller.user@tokit.com")
                .walletAddress("0xCONTROLLER_USER_ADDRESS_01")
                .kycStatus(true)
                .build();
        setField(testUser, "id", 1L);
    }

    @Test
    @DisplayName("POST /api/users/signup: 올바른 회원가입 정보 제출 시 신규 회원 생성 성공 및 HTTP 200을 반환한다.")
    void signUp_Success() throws Exception {
        // Given
        UserController.SignUpRequest request = new UserController.SignUpRequest(
                "controller.user@tokit.com", "Controller Investor", "tokit1234", "0xCONTROLLER_USER_ADDRESS_01"
        );

        when(userService.signUp(any(), any(), any(), any())).thenReturn(testUser);

        // When & Then
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("controller.user@tokit.com"));
    }

    @Test
    @DisplayName("POST /api/users/signup: 이메일 형식이 비올바를 경우 Valid 검증으로 HTTP 400을 반환한다.")
    void signUp_ValidationError_Returns400() throws Exception {
        // Given: 비올바른 이메일 형식
        UserController.SignUpRequest request = new UserController.SignUpRequest(
                "invalid-email-format", "Controller Investor", "tokit1234", "0xCONTROLLER_USER_ADDRESS_01"
        );

        // When & Then
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/users/{id}: 사용자 ID 단건 조회가 성공하여 HTTP 200을 반환한다.")
    void getUser_Success() throws Exception {
        // Given
        when(userService.getUserById(1L)).thenReturn(testUser);

        // When & Then
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.kycStatus").value(true));
    }

    @Test
    @DisplayName("PUT /api/users/admin/{id}/kyc: 운영자가 특정 사용자의 KYC 자격을 강제 조정하면 HTTP 200을 반환한다.")
    void updateKycAsAdmin_Success() throws Exception {
        // Given: 정상 승인 경로는 /api/kyc/verifications이며, 이 엔드포인트는 운영자 예외 조치용입니다.
        when(userService.updateKycStatus(eq(1L), eq(true))).thenReturn(testUser);

        // When & Then
        mockMvc.perform(put("/api/users/admin/1/kyc")
                        .param("kycStatus", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.kycStatus").value(true));
    }

    @Test
    @DisplayName("GET /api/users/{id}: 다른 사용자의 ID로 조회하면 조회 없이 HTTP 403을 반환한다.")
    void getUser_OtherUser_Returns403() throws Exception {
        // When & Then: 인증 주체는 1번, 요청 대상은 2번
        mockMvc.perform(get("/api/users/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("GET /api/users/{id}: 관리자는 다른 사용자의 정보도 조회할 수 있다.")
    void getUser_AdminCanViewOtherUser() throws Exception {
        // Given
        MockMvc adminMvc = mockMvcAs(99L, Role.ADMIN);
        when(userService.getUserById(1L)).thenReturn(testUser);

        // When & Then
        adminMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /api/users: 관리자가 전체 사용자 목록을 조회하면 HTTP 200을 반환한다.")
    void getAllUsers_Admin_Success() throws Exception {
        // Given
        MockMvc adminMvc = mockMvcAs(99L, Role.ADMIN);
        when(userService.getAllUsers()).thenReturn(List.of(testUser));

        // When & Then
        adminMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/users: 일반 사용자가 전체 사용자 목록을 조회하면 HTTP 403을 반환한다.")
    void getAllUsers_NonAdmin_Returns403() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(userService);
    }

    private MockMvc mockMvcAs(Long userId, Role role) {
        return MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new TestAuthPrincipalResolver(userId, "as.user@tokit.com", role))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
