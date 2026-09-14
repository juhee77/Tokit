package com.tokit.domain.wallet.controller;

import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.wallet.service.WalletService;
import com.tokit.global.annotation.Idempotent;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import com.tokit.domain.wallet.dto.WalletSummaryResponse;

@Tag(name = "03. Wallet (지갑)", description = "사용자 지갑(예치금 및 자산) 관리 API")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/summary")
    @Operation(summary = "지갑 포트폴리오 요약 조회", description = "로그인한 사용자의 원화 예치금, 토큰증권 보유 자산 평가액 및 종합 포트폴리오 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getWalletSummary(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        WalletSummaryResponse response = walletService.getWalletSummary(authUser.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record WalletAmountRequest(
            @NotNull(message = "금액은 필수입니다.")
            @Positive(message = "금액은 양수여야 합니다.")
            BigDecimal amount
    ) {}

    @PostMapping("/deposit")
    @Idempotent
    @Operation(summary = "원화(KRW) 예치금 충전", description = "로그인한 사용자의 원화 지갑에 예치금을 충전합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<WalletResponse>> depositKrw(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid WalletAmountRequest request
    ) {
        WalletResponse response = walletService.depositKrw(authUser.id(), request.amount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/withdraw")
    @Idempotent
    @Operation(summary = "원화(KRW) 예치금 출금", description = "로그인한 사용자의 원화 지갑에서 예치금을 출금합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<WalletResponse>> withdrawKrw(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid WalletAmountRequest request
    ) {
        WalletResponse response = walletService.withdrawKrw(authUser.id(), request.amount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
