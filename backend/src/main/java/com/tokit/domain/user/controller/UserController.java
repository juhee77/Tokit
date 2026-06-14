package com.tokit.domain.user.controller;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.service.UserService;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. User (사용자)", description = "회원가입 및 사용자 정보 조회 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record SignUpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Wallet Address is required") String walletAddress
    ) {}

    public record UserResponse(
        Long id,
        String email,
        String name,
        String walletAddress,
        boolean kycStatus
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getWalletAddress(), user.isKycStatus());
        }
    }

    @PostMapping("/signup")
    @Operation(summary = "회원 가입", description = "이메일, 이름, 지갑 주소를 입력받아 회원 가입을 처리합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody @Valid SignUpRequest request) {
        User user = userService.signUp(request.email(), request.name(), request.walletAddress());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 조회", description = "사용자 ID로 해당 사용자의 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable("id") Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }
}
