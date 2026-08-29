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

@Tag(name = "03. Wallet (지갑)", description = "사용자 지갑(예치금 및 자산) 관리 API")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

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
