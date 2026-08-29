package com.tokit.domain.user.controller;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.service.UserService;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "01. User (사용자)", description = "회원가입 및 사용자 정보 조회 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record SignUpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,
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
    @Operation(summary = "회원 가입", description = "이메일, 이름, 비밀번호, 지갑 주소를 입력받아 회원 가입을 처리합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody @Valid SignUpRequest request) {
        User user = userService.signUp(request.email(), request.name(), request.password(), request.walletAddress());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 조회", description = "사용자 ID로 해당 사용자의 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable("id") Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    // 실명확인 승인은 KycController를 통해서만 이뤄집니다. 이 엔드포인트는 운영자가
    // 사후 제재 확인 등으로 자격을 조정할 때 쓰는 예외 경로이며, 심사 이력이 남지 않습니다.
    @PutMapping("/admin/{id}/kyc")
    @Operation(summary = "[관리자] 특정 사용자 KYC 상태 강제 변경",
               description = "운영자가 지정한 사용자의 KYC 자격을 직접 조정합니다. 정상 승인 경로는 /api/kyc/verifications 입니다.")
    public ResponseEntity<ApiResponse<UserResponse>> updateKycAsAdmin(
            @PathVariable("id") Long id,
            @RequestParam("kycStatus") boolean kycStatus
    ) {
        User user = userService.updateKycStatus(id, kycStatus);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping
    @Operation(summary = "전체 사용자 조회", description = "시스템에 등록된 전체 사용자 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> list = userService.getAllUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
