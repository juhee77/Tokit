package com.tokit.domain.asset.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.service.AssetService;
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
import java.util.List;

@Tag(name = "02. Asset (토큰증권 자산)", description = "토큰증권(STO) 기초자산 등록 및 조회 API")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    public record RegisterAssetRequest(
        @NotBlank(message = "Symbol is required") String symbol,
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Contract Address is required") String contractAddress,
        @NotNull(message = "Total Supply is required") @Positive(message = "Total supply must be positive") BigDecimal totalSupply
    ) {}

    public record AssetResponse(
        Long id,
        String symbol,
        String name,
        String contractAddress,
        BigDecimal totalSupply
    ) {
        public static AssetResponse from(Asset asset) {
            return new AssetResponse(asset.getId(), asset.getSymbol(), asset.getName(), asset.getContractAddress(), asset.getTotalSupply());
        }
    }

    @PostMapping
    @Operation(summary = "토큰증권 자산 등록", description = "기초자산의 심볼, 이름, 스마트 컨트랙트 주소, 총 발행량을 입력하여 자산을 등록합니다.")
    public ResponseEntity<ApiResponse<AssetResponse>> registerAsset(@RequestBody @Valid RegisterAssetRequest request) {
        Asset asset = assetService.registerAsset(request.symbol(), request.name(), request.contractAddress(), request.totalSupply());
        return ResponseEntity.ok(ApiResponse.success(AssetResponse.from(asset)));
    }

    @GetMapping
    @Operation(summary = "전체 토큰증권 자산 목록 조회", description = "플랫폼에 등록된 전체 토큰증권 자산 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAllAssets() {
        List<AssetResponse> list = assetService.getAllAssets().stream()
                .map(AssetResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "심볼로 토큰증권 자산 상세 조회", description = "자산 심볼(예: APPL-STO)로 자산의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<AssetResponse>> getAssetBySymbol(@PathVariable("symbol") String symbol) {
        Asset asset = assetService.getAssetBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success(AssetResponse.from(asset)));
    }
}
