package com.tokit.domain.auth.controller;

import com.tokit.domain.auth.service.AuthService;
import com.tokit.domain.user.entity.User;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "00. Auth (인증)", description = "로그인 및 인증 토큰 발급 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Password is required") String password
    ) {}

    public record LoginResponse(
        String accessToken,
        long expiresInMs,
        Long userId,
        String email,
        String name
    ) {}

    public record MeResponse(Long userId, String email) {}

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 인증하고 액세스 토큰을 발급합니다.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        User user = result.user();
        return ResponseEntity.ok(ApiResponse.success(new LoginResponse(
                result.accessToken(),
                result.expiresInMs(),
                user.getId(),
                user.getEmail(),
                user.getName()
        )));
    }

    @GetMapping("/me")
    @Operation(summary = "내 인증 정보 조회", description = "현재 액세스 토큰에 해당하는 사용자 정보를 반환합니다.")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.success(new MeResponse(authUser.id(), authUser.email())));
    }
}
