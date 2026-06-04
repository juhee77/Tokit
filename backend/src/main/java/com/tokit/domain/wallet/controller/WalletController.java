package com.tokit.domain.wallet.controller;

import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.wallet.service.WalletService;
import com.tokit.global.annotation.Idempotent;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "03. Wallet (지갑)", description = "사용자 지갑(예치금 및 자산) 관리 API")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    public record WalletAmountRequest(
            @NotNull(message = "사용자 ID는 필수입니다.")
            Long userId,
            @NotNull(message = "금액은 필수입니다.")
            @Positive(message = "금액은 양수여야 합니다.")
            BigDecimal amount
    ) {}

    @PostMapping("/deposit")
    @Idempotent
    @Operation(summary = "원화(KRW) 예치금 충전", description = "사용자의 원화 지갑에 예치금을 충전합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<WalletResponse>> depositKrw(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid WalletAmountRequest request
    ) {
        WalletResponse response = walletService.depositKrw(request.userId(), request.amount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/withdraw")
    @Idempotent
    @Operation(summary = "원화(KRW) 예치금 출금", description = "사용자의 원화 지갑에서 예치금을 출금합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<WalletResponse>> withdrawKrw(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid WalletAmountRequest request
    ) {
        WalletResponse response = walletService.withdrawKrw(request.userId(), request.amount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
