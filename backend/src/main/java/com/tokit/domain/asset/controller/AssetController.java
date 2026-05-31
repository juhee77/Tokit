package com.tokit.domain.asset.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.service.AssetService;
import com.tokit.global.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
    public ResponseEntity<ApiResponse<AssetResponse>> registerAsset(@RequestBody @Valid RegisterAssetRequest request) {
        Asset asset = assetService.registerAsset(request.symbol(), request.name(), request.contractAddress(), request.totalSupply());
        return ResponseEntity.ok(ApiResponse.success(AssetResponse.from(asset)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssetResponse>>> getAllAssets() {
        List<AssetResponse> list = assetService.getAllAssets().stream()
                .map(AssetResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<AssetResponse>> getAssetBySymbol(@PathVariable("symbol") String symbol) {
        Asset asset = assetService.getAssetBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success(AssetResponse.from(asset)));
    }
}
