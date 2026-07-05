package com.tokit.domain.issuer.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.entity.AssetReport;
import com.tokit.domain.asset.repository.AssetReportRepository;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "10. Issuer Portal (발행사 포털)", description = "발행사 전용 자산 관리 및 보고서 API")
@RestController
@RequestMapping("/api/issuer")
@RequiredArgsConstructor
@Slf4j
public class IssuerController {

    private final AssetRepository assetRepository;
    private final WalletRepository walletRepository;
    private final AssetReportRepository assetReportRepository;

    private static final String UPLOAD_DIR = "uploads";

    // --- DTO Records ---
    public record IssuerAssetResponse(
            Long id,
            String symbol,
            String name,
            String contractAddress,
            BigDecimal totalSupply,
            BigDecimal issuePrice,
            String status,
            BigDecimal subscriptionProgress,
            long totalInvestors
    ) {}

    public record ShareholderDemographics(
            String name,
            String walletAddress,
            BigDecimal balance,
            BigDecimal shareRatio
    ) {}

    public record AssetReportResponse(
            Long id,
            String title,
            String filePath,
            String createdAt
    ) {
        public static AssetReportResponse from(AssetReport report) {
            return new AssetReportResponse(
                    report.getId(),
                    report.getTitle(),
                    report.getFilePath(),
                    report.getCreatedAt().toString()
            );
        }
    }

    @GetMapping("/assets")
    @Operation(summary = "발행사 자산 목록 조회", description = "특정 발행사가 발행한 모든 STO 자산 목록 및 요약 통계를 조회합니다.")
    public ResponseEntity<ApiResponse<List<IssuerAssetResponse>>> getIssuerAssets(@RequestParam("issuerId") Long issuerId) {
        List<Asset> assets = assetRepository.findByIssuer_Id(issuerId);

        List<IssuerAssetResponse> response = assets.stream().map(asset -> {
            BigDecimal sumBalance = walletRepository.sumBalanceByAssetId(asset.getId());
            long investors = walletRepository.countInvestorsByAssetId(asset.getId());

            BigDecimal progress = BigDecimal.ZERO;
            if (asset.getTotalSupply().compareTo(BigDecimal.ZERO) > 0) {
                progress = sumBalance.multiply(BigDecimal.valueOf(100))
                        .divide(asset.getTotalSupply(), 2, RoundingMode.HALF_UP);
            }
            if ("상장완료".equals(asset.getStatus()) || "거래중".equals(asset.getStatus())) {
                progress = BigDecimal.valueOf(100);
            }

            return new IssuerAssetResponse(
                    asset.getId(),
                    asset.getSymbol(),
                    asset.getName(),
                    asset.getContractAddress(),
                    asset.getTotalSupply(),
                    asset.getIssuePrice(),
                    asset.getStatus(),
                    progress,
                    investors
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/assets/{symbol}/investors")
    @Operation(summary = "주주 지분 분포 통계 조회", description = "특정 STO 자산을 보유 중인 주주들의 지분 비율 및 보유량을 내림차순으로 조회합니다.")
    public ResponseEntity<ApiResponse<List<ShareholderDemographics>>> getShareholderDemographics(
            @PathVariable String symbol,
            @RequestParam("issuerId") Long issuerId
    ) {
        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + symbol));

        if (!asset.getIssuer().getId().equals(issuerId)) {
            throw new IllegalArgumentException("Unauthorized access to this asset");
        }

        List<Wallet> wallets = walletRepository.findByAsset_IdAndBalanceGreaterThanOrderByBalanceDesc(asset.getId(), BigDecimal.ZERO);

        List<ShareholderDemographics> demographics = wallets.stream().map(wallet -> {
            BigDecimal shareRatio = BigDecimal.ZERO;
            if (asset.getTotalSupply().compareTo(BigDecimal.ZERO) > 0) {
                shareRatio = wallet.getBalance().multiply(BigDecimal.valueOf(100))
                        .divide(asset.getTotalSupply(), 2, RoundingMode.HALF_UP);
            }
            return new ShareholderDemographics(
                    wallet.getUser().getName(),
                    wallet.getUser().getWalletAddress(),
                    wallet.getBalance(),
                    shareRatio
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(demographics));
    }

    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "공시 보고서 업로드", description = "발행인이 자사 STO 상품 관련 투자자 공시용 보고서를 업로드합니다.")
    public ResponseEntity<ApiResponse<AssetReportResponse>> uploadReport(
            @RequestParam("assetId") Long assetId,
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            @RequestParam("issuerId") Long issuerId
    ) throws IOException {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (!asset.getIssuer().getId().equals(issuerId)) {
            throw new IllegalArgumentException("Unauthorized access to this asset");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".pdf";
        String savedFilename = UUID.randomUUID().toString() + extension;
        Path targetPath = Paths.get(UPLOAD_DIR).resolve(savedFilename);

        Files.copy(file.getInputStream(), targetPath);

        AssetReport report = AssetReport.builder()
                .asset(asset)
                .title(title)
                .filePath("/api/issuer/reports/download/" + savedFilename)
                .createdAt(LocalDateTime.now())
                .build();

        AssetReport savedReport = assetReportRepository.save(report);
        return ResponseEntity.ok(ApiResponse.success(AssetReportResponse.from(savedReport)));
    }

    @GetMapping("/assets/{symbol}/reports")
    @Operation(summary = "공시 보고서 목록 조회", description = "특정 STO 자산에 기 등록된 공시 보고서 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<AssetReportResponse>>> getAssetReports(
            @PathVariable String symbol,
            @RequestParam("issuerId") Long issuerId
    ) {
        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (!asset.getIssuer().getId().equals(issuerId)) {
            throw new IllegalArgumentException("Unauthorized access to this asset");
        }

        List<AssetReport> reports = assetReportRepository.findByAsset_IdOrderByCreatedAtDesc(asset.getId());
        List<AssetReportResponse> response = reports.stream()
                .map(AssetReportResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reports/download/{filename:.+}")
    @Operation(summary = "공시 보고서 파일 다운로드", description = "업로드된 공시 보고서 파일을 다운로드합니다.")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(UPLOAD_DIR).resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
