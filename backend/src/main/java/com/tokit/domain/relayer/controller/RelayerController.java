package com.tokit.domain.relayer.controller;

import com.tokit.domain.relayer.service.RelayerService;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "11. 안전 이체 릴레이 서비스 (수수료 면제)", description = "투자자 전자서명 기반 수수료 면제 이체 및 오프체인 거래 순서 번호(Nonce) 제공 API")
@RestController
@RequestMapping("/api/relayer")
@RequiredArgsConstructor
public class RelayerController {

    private final RelayerService relayerService;

    public record RelayTransferRequest(
            @NotBlank(message = "From address is required")
            String fromAddress,

            @NotBlank(message = "To address is required")
            String toAddress,

            @NotBlank(message = "Asset symbol is required")
            String assetSymbol,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Nonce is required")
            Long nonce,

            @NotBlank(message = "Signature is required")
            String signature
    ) {}

    public record NonceResponse(
            String walletAddress,
            Long nonce
    ) {}

    @GetMapping("/nonce/{address}")
    @Operation(summary = "이체 거래 순서 번호(Nonce) 조회", description = "특정 지갑 주소의 중복 호출 방지 및 거래 서명용 다음 순서 번호를 조회합니다.")
    public ResponseEntity<ApiResponse<NonceResponse>> getNonce(@PathVariable String address) {
        Long nonce = relayerService.getOrCreateNonce(address);
        return ResponseEntity.ok(ApiResponse.success(new NonceResponse(address, nonce)));
    }

    @PostMapping("/transfer")
    @Operation(summary = "수수료 면제 안전 이체 승인", description = "전자서명 정보를 검증한 후, 별도의 수수료(Gas fee) 부과 없이 자산을 다른 주소로 안전하게 이체합니다.")
    public ResponseEntity<ApiResponse<String>> relayTransfer(
            @RequestBody @Valid RelayTransferRequest request
    ) {
        relayerService.verifySignatureAndTransfer(
                request.fromAddress(),
                request.toAddress(),
                request.assetSymbol(),
                request.amount(),
                request.nonce(),
                request.signature()
        );
        return ResponseEntity.ok(ApiResponse.success("SUCCESS"));
    }
}
