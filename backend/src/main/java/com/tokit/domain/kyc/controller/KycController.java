package com.tokit.domain.kyc.controller;

import com.tokit.domain.kyc.entity.KycVerification;
import com.tokit.domain.kyc.provider.KycVerificationRequest;
import com.tokit.domain.kyc.provider.KycVerificationResult;
import com.tokit.domain.kyc.service.KycService;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "02. KYC (실명확인)", description = "투자자 실명확인 신청 및 심사 이력 API")
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    public record VerificationSubmitRequest(
            @NotBlank(message = "실명은 필수입니다.") String legalName,
            @NotNull(message = "생년월일은 필수입니다.") LocalDate dateOfBirth,
            @NotBlank(message = "주민등록번호 뒤 7자리는 필수입니다.") String nationalIdLast7,
            @NotBlank(message = "휴대전화 번호는 필수입니다.") String phoneNumber
    ) {}

    public record VerificationResponse(
            Long id,
            KycVerificationResult.KycStatus status,
            String provider,
            String rejectReason,
            LocalDateTime verifiedAt
    ) {
        public static VerificationResponse from(KycVerification verification) {
            return new VerificationResponse(
                    verification.getId(),
                    verification.getStatus(),
                    verification.getProvider(),
                    verification.getRejectReason(),
                    verification.getVerifiedAt()
            );
        }
    }

    public record RevokeRequest(@NotBlank(message = "회수 사유는 필수입니다.") String reason) {}

    @PostMapping("/verifications")
    @Operation(summary = "실명확인 신청",
               description = "실명확인 제공자에게 신원확인을 요청하고, 승인된 경우에만 거래 자격이 부여됩니다.")
    public ResponseEntity<ApiResponse<VerificationResponse>> submit(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid VerificationSubmitRequest request
    ) {
        KycVerification result = kycService.submitVerification(
                authUser.id(),
                new KycVerificationRequest(
                        authUser.id(),
                        request.legalName(),
                        request.dateOfBirth(),
                        request.nationalIdLast7(),
                        request.phoneNumber(),
                        null
                )
        );
        return ResponseEntity.ok(ApiResponse.success(VerificationResponse.from(result)));
    }

    @GetMapping("/verifications/me")
    @Operation(summary = "내 실명확인 이력 조회", description = "로그인한 사용자의 실명확인 시도 이력을 최신순으로 반환합니다.")
    public ResponseEntity<ApiResponse<List<VerificationResponse>>> myHistory(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<VerificationResponse> history = kycService.getVerificationHistory(authUser.id()).stream()
                .map(VerificationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/admin/{userId}/revoke")
    @Operation(summary = "[관리자] 실명확인 자격 회수",
               description = "제재 대상 확인 등으로 투자자의 거래 자격을 회수하고 온체인 화이트리스트에서 제거합니다.")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable("userId") Long userId,
            @RequestBody @Valid RevokeRequest request
    ) {
        kycService.revokeVerification(userId, request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
