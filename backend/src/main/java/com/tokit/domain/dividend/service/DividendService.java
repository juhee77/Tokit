package com.tokit.domain.dividend.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.service.AssetService;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DividendService {

    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendPayoutDetailRepository dividendPayoutDetailRepository;
    private final AssetService assetService;
    private final JobLauncher jobLauncher;
    private final Job dividendPayoutJob;

    @Transactional
    public DividendPayout createDividendPayout(Long assetId, BigDecimal totalDividendAmount) {
        log.info("[Dividend Service] Registering dividend payout. AssetId: {}, Amount: {}", assetId, totalDividendAmount);
        Asset asset = assetService.getAssetById(assetId);

        // 1. PENDING 상태로 배당 이력 우선 등록
        DividendPayout payout = DividendPayout.builder()
                .asset(asset)
                .totalDividendAmount(totalDividendAmount)
                .payoutDate(LocalDateTime.now())
                .status("PENDING")
                .build();
        dividendPayoutRepository.save(payout);

        // 2. 비동기식으로 배당 지급 배치 가동
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[Dividend Service] Triggering batch for payout ID: {}", payout.getId());
                JobParameters params = new JobParametersBuilder()
                        .addLong("payoutId", payout.getId())
                        .addLong("time", System.currentTimeMillis())
                        .toJobParameters();
                var execution = jobLauncher.run(dividendPayoutJob, params);
                log.info("[Dividend Service] Batch status for payout ID {}: {}", payout.getId(), execution.getStatus());
            } catch (Exception e) {
                log.error("[Dividend Service] CRITICAL: Failed to launch dividend payout batch job!", e);
                // 트랜잭션이 분리되어 비동기 스레드에서 직접 상태를 FAILED로 업데이트
                updatePayoutStatus(payout.getId(), "FAILED");
            }
        });

        return payout;
    }

    @Transactional
    public void updatePayoutStatus(Long payoutId, String status) {
        DividendPayout payout = dividendPayoutRepository.findById(payoutId).orElse(null);
        if (payout != null) {
            payout.updateStatus(status);
            dividendPayoutRepository.save(payout);
        }
    }

    public List<DividendPayout> getAllDividends() {
        return dividendPayoutRepository.findAll();
    }

    public List<DividendPayoutDetail> getDividendDetails(Long payoutId) {
        return dividendPayoutDetailRepository.findByPayout_Id(payoutId);
    }
}
