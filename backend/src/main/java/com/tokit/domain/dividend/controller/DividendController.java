package com.tokit.domain.dividend.controller;

import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.service.DividendService;
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
import java.util.List;

@Tag(name = "06. Dividend (배당금 관리)", description = "어드민 전용 STO 배당 지급 등록, 이력 및 상세 조회 API")
@RestController
@RequestMapping("/api/admin/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    // --- Request DTO ---
    public record CreateDividendRequest(
            @NotNull(message = "Asset ID는 필수입니다.")
            Long assetId,

            @NotNull(message = "총 배당 금액은 필수입니다.")
            @Positive(message = "배당 금액은 양수여야 합니다.")
            BigDecimal totalDividendAmount
    ) {}

    // --- Response DTO ---
    public record DividendResponse(
            Long id,
            Long assetId,
            String assetSymbol,
            String assetName,
            BigDecimal totalDividendAmount,
            String payoutDate,
            String status
    ) {
        public static DividendResponse from(DividendPayout payout) {
            return new DividendResponse(
                    payout.getId(),
                    payout.getAsset().getId(),
                    payout.getAsset().getSymbol(),
                    payout.getAsset().getName(),
                    payout.getTotalDividendAmount(),
                    payout.getPayoutDate().toString(),
                    payout.getStatus()
            );
        }
    }

    public record DividendDetailResponse(
            Long id,
            Long userId,
            String userName,
            String walletAddress,
            BigDecimal shareRatio,
            BigDecimal payoutAmount,
            String status,
            String errorMessage
    ) {
        public static DividendDetailResponse from(DividendPayoutDetail detail) {
            return new DividendDetailResponse(
                    detail.getId(),
                    detail.getUser().getId(),
                    detail.getUser().getName(),
                    detail.getWalletAddress(),
                    detail.getShareRatio(),
                    detail.getPayoutAmount(),
                    detail.getStatus(),
                    detail.getErrorMessage()
            );
        }
    }

    @PostMapping
    @Operation(summary = "배당금 지급 등록 및 실행", description = "특정 자산에 배당금을 배정하고 배당 자동 지급 배치를 실행합니다.")
    public ResponseEntity<ApiResponse<DividendResponse>> createDividendPayout(
            @RequestBody @Valid CreateDividendRequest request
    ) {
        DividendPayout payout = dividendService.createDividendPayout(request.assetId(), request.totalDividendAmount());
        return ResponseEntity.ok(ApiResponse.success(DividendResponse.from(payout)));
    }

    @GetMapping
    @Operation(summary = "전체 배당금 지급 이력 조회", description = "플랫폼에서 실행된 전체 배당금 지급 이력 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<DividendResponse>>> getAllDividends() {
        List<DividendResponse> list = dividendService.getAllDividends().stream()
                .map(DividendResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{payoutId}/details")
    @Operation(summary = "배당금 지급 상세 내역 조회", description = "특정 배당금 지급 건에 참여한 개별 주주별 지급 상태 및 수령 금액 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<List<DividendDetailResponse>>> getDividendDetails(
            @PathVariable("payoutId") Long payoutId
    ) {
        List<DividendDetailResponse> list = dividendService.getDividendDetails(payoutId).stream()
                .map(DividendDetailResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
