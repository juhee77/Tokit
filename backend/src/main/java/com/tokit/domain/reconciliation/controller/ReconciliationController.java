package com.tokit.domain.reconciliation.controller;

import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import com.tokit.domain.reconciliation.repository.ReconciliationLogRepository;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "08. Reconciliation (온-오프체인 데이터 대사)", description = "온-오프체인 잔고 대조 및 배치 실행 API")
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;
    private final ReconciliationLogRepository reconciliationLogRepository;

    public record RunBatchResponse(String status) {}

    public record ReconciliationLogResponse(
        Long id,
        Long userId,
        String userName,
        Long assetId,
        String assetSymbol,
        String walletAddress,
        BigDecimal offchainBalance,
        BigDecimal onchainBalance,
        BigDecimal difference,
        String checkedAt
    ) {
        public static ReconciliationLogResponse from(ReconciliationLog log) {
            return new ReconciliationLogResponse(
                    log.getId(),
                    log.getUser().getId(),
                    log.getUser().getName(),
                    log.getAsset().getId(),
                    log.getAsset().getSymbol(),
                    log.getWalletAddress(),
                    log.getOffchainBalance(),
                    log.getOnchainBalance(),
                    log.getDifference(),
                    log.getCheckedAt().toString()
            );
        }
    }

    @PostMapping("/run")
    @Operation(summary = "정합성 대사 배치 실행", description = "온-오프체인 잔고 정합성을 비교하는 배치 작업을 즉시 실행합니다.")
    public ResponseEntity<ApiResponse<RunBatchResponse>> runReconciliation() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(reconciliationJob, params);
            return ResponseEntity.ok(ApiResponse.success(new RunBatchResponse(execution.getStatus().toString())));
        } catch (Exception e) {
            throw new RuntimeException("Failed to run reconciliation batch", e);
        }
    }

    @GetMapping("/logs")
    @Operation(summary = "대사 오류 로그 조회", description = "정합성 불일치가 감지된 대사 로그 이력을 모두 조회합니다.")
    public ResponseEntity<ApiResponse<List<ReconciliationLogResponse>>> getReconciliationLogs() {
        List<ReconciliationLogResponse> list = reconciliationLogRepository.findAll().stream()
                .map(ReconciliationLogResponse::from)
                .sorted((a, b) -> b.checkedAt().compareTo(a.checkedAt())) // 최근 이력 우선
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
