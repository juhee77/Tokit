package com.tokit.global.config;

import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
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

    public DividendBatchConfig(@Lazy WalletRepository walletRepository,
                               @Lazy DividendPayoutRepository dividendPayoutRepository,
                               @Lazy DividendPayoutDetailRepository dividendPayoutDetailRepository) {
        this.walletRepository = walletRepository;
        this.dividendPayoutRepository = dividendPayoutRepository;
        this.dividendPayoutDetailRepository = dividendPayoutDetailRepository;
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
            // 1. 지분율 계산 (총 발행량 대비 주주 보유량)
            BigDecimal shareRatio = userTokenBalance.divide(totalSupply, 6, RoundingMode.HALF_UP);
            // 2. 배당금 계산 및 소수점 절사 (원화 예치금 단위 절사)
            BigDecimal payoutAmount = totalDividend.multiply(shareRatio).setScale(0, RoundingMode.DOWN);

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

    @Bean
    public JobExecutionListener dividendJobListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                Long payoutId = jobExecution.getJobParameters().getLong("payoutId");
                if (payoutId != null) {
                    DividendPayout payout = dividendPayoutRepository.findById(payoutId).orElse(null);
                    if (payout != null) {
                        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                            payout.updateStatus("COMPLETED");
                        } else {
                            payout.updateStatus("FAILED");
                        }
                        dividendPayoutRepository.save(payout);
                        log.info("[Dividend Payout Batch] Job ended. Dividend ID {} status updated to: {}", payoutId, payout.getStatus());
                    }
                }
            }
        };
    }
}
