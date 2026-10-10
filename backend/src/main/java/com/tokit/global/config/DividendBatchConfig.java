package com.tokit.global.config;

import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.domain.dividend.service.DividendSettlementService;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class DividendBatchConfig {

    private final WalletRepository walletRepository;
    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendPayoutDetailRepository dividendPayoutDetailRepository;
    private final DividendSettlementService dividendSettlementService;

    public DividendBatchConfig(@Lazy WalletRepository walletRepository,
                               @Lazy DividendPayoutRepository dividendPayoutRepository,
                               @Lazy DividendPayoutDetailRepository dividendPayoutDetailRepository,
                               @Lazy DividendSettlementService dividendSettlementService) {
        this.walletRepository = walletRepository;
        this.dividendPayoutRepository = dividendPayoutRepository;
        this.dividendPayoutDetailRepository = dividendPayoutDetailRepository;
        this.dividendSettlementService = dividendSettlementService;
    }

    @Bean
    public Job dividendPayoutJob(JobRepository jobRepository, Step dividendStep) {
        return new JobBuilder("dividendPayoutJob", jobRepository)
                .start(dividendStep)
                .listener(dividendJobListener())
                .build();
    }

    @Bean
    @JobScope
    public Step dividendStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             ItemReader<Wallet> dividendReader,
                             ItemProcessor<Wallet, DividendPayoutDetail> dividendProcessor,
                             ItemWriter<DividendPayoutDetail> dividendWriter) {
        return new StepBuilder("dividendStep", jobRepository)
                .<Wallet, DividendPayoutDetail>chunk(10, transactionManager)
                .reader(dividendReader)
                .processor(dividendProcessor)
                .writer(dividendWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<Wallet> dividendReader(@Value("#{jobParameters['payoutId']}") Long payoutId) {
        log.info("[Dividend Batch Reader] Loading token holders for payoutId: {}", payoutId);
        DividendPayout payout = dividendPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Dividend payout record not found for ID: " + payoutId));
        Long assetId = payout.getAsset().getId();

        return new RepositoryItemReaderBuilder<Wallet>()
                .name("dividendReader")
                .repository(walletRepository)
                .methodName("findByAsset_IdAndBalanceGreaterThan")
                .arguments(List.of(assetId, BigDecimal.ZERO))
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(10)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<Wallet, DividendPayoutDetail> dividendProcessor(@Value("#{jobParameters['payoutId']}") Long payoutId) {
        DividendPayout payout = dividendPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Dividend payout record not found for ID: " + payoutId));
        BigDecimal totalDividend = payout.getTotalDividendAmount();
        BigDecimal totalSupply = payout.getAsset().getTotalSupply();

        return wallet -> {
            BigDecimal userTokenBalance = wallet.getBalance().add(wallet.getLockedBalance());

            // 1. 지급액은 중간 반올림 없이 금액에서 직접 계산한다.
            //    지분율을 먼저 6자리로 반올림한 뒤 곱하면, 그 반올림 오차가 주주 수만큼
            //    누적되어 배당 재원을 초과한다. 주주 6명이 1/6씩 보유한 경우 지분율이
            //    0.1666666...에서 0.166667로 올라가 합이 1.000002가 되고, 재원 100만 원에
            //    100만 2원이 지급된다. 주주가 늘면 상대 오차도 함께 커져, 600명 규모에서는
            //    10억 원 재원에 20만 원이 더 나갔다.
            //    금액에서 한 번만 내림하면 지급 총액은 재원을 넘지 않는다.
            BigDecimal payoutAmount = totalDividend.multiply(userTokenBalance)
                    .divide(totalSupply, 0, RoundingMode.DOWN);

            // 2. 지분율은 기록·표시용이다. 지급액 계산에 쓰지 않으며, 합이 100%를 넘지
            //    않도록 내림한다.
            BigDecimal shareRatio = userTokenBalance.divide(totalSupply, 6, RoundingMode.DOWN);

            log.info("[Dividend Batch Processor] User: {} ({}), Balance: {} STO, Share Ratio: {}, Payout Amount: {} KRW",
                    wallet.getUser().getName(), wallet.getUser().getWalletAddress(), userTokenBalance, shareRatio, payoutAmount);

            return DividendPayoutDetail.builder()
                    .payout(payout)
                    .user(wallet.getUser())
                    .walletAddress(wallet.getUser().getWalletAddress())
                    .shareRatio(shareRatio)
                    .payoutAmount(payoutAmount)
                    .status("PENDING")
                    .build();
        };
    }

    @Bean
    @StepScope
    public ItemWriter<DividendPayoutDetail> dividendWriter() {
        return chunk -> {
            log.info("[Dividend Batch Writer] Executing KRW payout and detailing logs for chunk of size: {}", chunk.getItems().size());
            for (DividendPayoutDetail detail : chunk.getItems()) {
                try {
                    // 1. 유저의 KRW 예치금 지갑 락 걸고 조회 (기존에 락 없는 조회에서 락 거는 조회로 변경)
                    Wallet krwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(detail.getUser().getId())
                            .orElseThrow(() -> new IllegalArgumentException("KRW wallet not found for user: " + detail.getUser().getId()));

                    // 2. 예치금 충전 (배당금 덧셈)
                    krwWallet.updateBalance(krwWallet.getBalance().add(detail.getPayoutAmount()), krwWallet.getLockedBalance());
                    walletRepository.save(krwWallet);

                    detail.updateStatus("SUCCESS", null);
                    log.info("[Dividend Batch Writer] Successfully paid {} KRW to User: {}", detail.getPayoutAmount(), detail.getUser().getId());
                } catch (Exception e) {
                    log.error("[Dividend Batch Writer] Failed to pay dividend to User: {}", detail.getUser().getId(), e);
                    detail.updateStatus("FAILED", e.getMessage());
                }
            }
            dividendPayoutDetailRepository.saveAll(chunk.getItems());
        };
    }

    /**
     * 배치 종료 시점에 정산을 위임한다.
     *
     * <p>{@code afterJob}은 트랜잭션과 영속성 세션 밖에서 실행되므로 지연 로딩을 건드릴 수
     * 없다. 조회·계산·이체를 한 트랜잭션으로 묶어야 하므로 정산은 전용 서비스가 수행한다.
     */
    @Bean
    public JobExecutionListener dividendJobListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                Long payoutId = jobExecution.getJobParameters().getLong("payoutId");
                if (payoutId == null) {
                    return;
                }
                dividendSettlementService.settle(
                        payoutId, jobExecution.getStatus() == BatchStatus.COMPLETED);
            }
        };
    }

}
