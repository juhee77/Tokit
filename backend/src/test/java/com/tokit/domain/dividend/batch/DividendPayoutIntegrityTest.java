package com.tokit.domain.dividend.batch;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

/**
 * 배당 분배 배치의 정합성 검증.
 *
 * <p>README는 배당을 "지분 비율대로 오차 없이 배분"한다고 설명한다. 배당은 금액을 쪼개
 * 나누는 연산이므로 주주 수가 늘면 각자의 반올림 오차가 누적된다. 그 누적분이 어느 방향으로
 * 쌓이는지가 중요하다. 아래로 쌓이면 미분배 잔액이 남을 뿐이지만, <b>위로 쌓이면 배당 재원보다
 * 많이 지급된다.</b> 발행사가 넣지 않은 돈이 투자자에게 나가는 것이므로 원장이 맞지 않는다.
 *
 * <p>그래서 특정 금액의 기댓값이 아니라 <b>불변식</b>을 검증한다.
 * 주주가 몇 명이든, 재원이 얼마든 성립해야 하는 성질이다.
 */
@SpringBootTest
class DividendPayoutIntegrityTest {

    @Autowired private JobLauncher jobLauncher;
    @Autowired @Qualifier("dividendPayoutJob") private Job dividendPayoutJob;

    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;
    @Autowired private DividendPayoutRepository payoutRepository;
    @Autowired private DividendPayoutDetailRepository detailRepository;

    @MockitoBean private ContractService contractService;

    private Issuer issuer;

    @BeforeEach
    void setUp() {
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());
        issuer = issuerRepository.save(Issuer.builder()
                .companyName("Dividend Test Issuer")
                .bizRegNo("div-" + UUID.randomUUID())
                .build());
    }

    @AfterEach
    void tearDown() {
        detailRepository.deleteAll();
        payoutRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("주주 6명이 전량을 균등 보유할 때 지급 총액이 배당 재원을 넘지 않는다.")
    void sixHoldersMustNotExceedDividendPool() throws Exception {
        // 지분율 1/6 = 0.1666666... 는 소수 6자리에서 올림되면 0.166667이 되고,
        // 6명의 합은 1.000002가 되어 재원을 넘어선다.
        assertPoolIsNotExceeded(6, BigDecimal.valueOf(1_000_000));
    }

    @Test
    @DisplayName("주주 600명 규모에서도 지급 총액이 배당 재원을 넘지 않는다.")
    void sixHundredHoldersMustNotExceedDividendPool() throws Exception {
        // 주주 수가 늘면 올림 오차가 함께 쌓여 초과액의 절대 규모가 커진다.
        assertPoolIsNotExceeded(600, BigDecimal.valueOf(1_000_000_000));
    }

    /**
     * 주주 전원이 자산을 1토큰씩 균등 보유한 상태에서 배당을 집행하고 불변식을 검증한다.
     *
     * @param holderCount 주주 수. 총 발행량과 같게 두어 전량이 분배 대상이 되게 한다.
     */
    private void assertPoolIsNotExceeded(int holderCount, BigDecimal pool) throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Asset asset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Dividend Test Asset")
                .symbol("DIV-" + uid)
                .totalSupply(BigDecimal.valueOf(holderCount))   // 전량이 주주에게 있다
                .issuePrice(BigDecimal.valueOf(10000))
                .status("거래중")
                .contractAddress("0xDividendTest")
                .build());

        List<User> holders = new ArrayList<>();
        for (int i = 0; i < holderCount; i++) {
            User holder = userRepository.save(User.builder()
                    .name("holder" + i)
                    .email("div-" + uid + "-" + i + "@test.com")
                    .password("{noop}test-password")
                    .walletAddress("0xDIV" + uid + i)
                    .kycStatus(true)
                    .build());
            holders.add(holder);
            // 자산 지갑: 1토큰씩 균등 보유
            walletRepository.save(Wallet.builder().user(holder).asset(asset)
                    .balance(BigDecimal.ONE).lockedBalance(BigDecimal.ZERO).build());
            // 원화 지갑: 배당을 받을 곳
            walletRepository.save(Wallet.builder().user(holder).asset(null)
                    .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build());
        }

        DividendPayout payout = payoutRepository.save(DividendPayout.builder()
                .asset(asset)
                .totalDividendAmount(pool)
                .payoutDate(LocalDateTime.now())
                .status("PENDING")
                .build());

        JobParameters params = new JobParametersBuilder()
                .addLong("payoutId", payout.getId())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(dividendPayoutJob, params);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<DividendPayoutDetail> details = detailRepository.findAll().stream()
                .filter(d -> d.getPayout().getId().equals(payout.getId()))
                .toList();

        BigDecimal paidTotal = details.stream()
                .map(DividendPayoutDetail::getPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ratioTotal = details.stream()
                .map(DividendPayoutDetail::getShareRatio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditedTotal = holders.stream()
                .map(h -> walletRepository.findKrwWalletByUserId(h.getId()).orElseThrow().getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertSoftly(softly -> {
            softly.assertThat(details).as("주주 전원에게 배당 내역이 생성되어야 한다").hasSize(holderCount);

            // 1. 발행사가 넣은 재원보다 많이 지급될 수 없다.
            softly.assertThat(paidTotal)
                    .as("지급 총액(%s)이 배당 재원(%s)을 초과했다", paidTotal, pool)
                    .isLessThanOrEqualTo(pool);

            // 2. 지분율의 합은 1(=100%)을 넘을 수 없다. 넘으면 없는 지분에 배당한 셈이다.
            softly.assertThat(ratioTotal)
                    .as("지분율 합(%s)이 100%%를 초과했다", ratioTotal)
                    .isLessThanOrEqualTo(BigDecimal.ONE);

            // 3. 원장에 기록한 금액과 실제 지갑에 들어간 금액이 일치해야 한다.
            softly.assertThat(creditedTotal.compareTo(paidTotal))
                    .as("지갑 입금 총액(%s)과 배당 내역 합(%s)이 다르다", creditedTotal, paidTotal)
                    .isZero();

            // 4. 초과를 막으려다 과소 지급이 되어서도 안 된다. 절사로 남을 수 있는 잔액은
            //    주주 1명당 최대 1원이므로, 미분배 잔액은 주주 수를 넘지 못한다.
            BigDecimal undistributed = pool.subtract(paidTotal);
            softly.assertThat(undistributed)
                    .as("미분배 잔액(%s)이 절사 한도(주주 수 %d원)를 넘었다 — 과소 지급", undistributed, holderCount)
                    .isLessThan(BigDecimal.valueOf(holderCount));

            // 5. 균등 보유이므로 지급액도 균등해야 한다. (절사 단위 1원 이내)
            BigDecimal max = details.stream().map(DividendPayoutDetail::getPayoutAmount)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal min = details.stream().map(DividendPayoutDetail::getPayoutAmount)
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            softly.assertThat(max.subtract(min))
                    .as("균등 보유인데 지급액 차이가 1원을 넘는다 (최대 %s, 최소 %s)", max, min)
                    .isLessThanOrEqualTo(BigDecimal.ONE);

            // 6. 전원이 PENDING이 아니라 SUCCESS로 마감되어야 한다.
            softly.assertThat(details).allSatisfy(d ->
                    assertThat(d.getStatus())
                            .as("배당 내역이 SUCCESS로 마감되지 않았다")
                            .isEqualTo("SUCCESS"));

            // 7. 보존 법칙: 재원 = 분배 총액 + 미분배 잔액.
            //    집행 결과가 원장에 기록되므로 상세 내역을 합산하지 않고 직접 대조한다.
            DividendPayout settled = payoutRepository.findById(payout.getId()).orElseThrow();
            softly.assertThat(settled.getDistributedAmount().add(settled.getUndistributedAmount())
                            .compareTo(pool))
                    .as("재원(%s) != 분배(%s) + 미분배(%s)",
                            pool, settled.getDistributedAmount(), settled.getUndistributedAmount())
                    .isZero();
            softly.assertThat(settled.getDistributedAmount().compareTo(paidTotal))
                    .as("기록된 분배 총액(%s)과 실제 지급 합(%s)이 다르다",
                            settled.getDistributedAmount(), paidTotal)
                    .isZero();

            // 8. 절사로 남은 잔액은 발행사 계정으로 돌아가야 한다. 아무 데도 넣지 않으면
            //    재원에서 빠진 채 원장에서 사라진다.
            Issuer settledIssuer = issuerRepository.findById(issuer.getId()).orElseThrow();
            softly.assertThat(settledIssuer.getUser())
                    .as("발행사 정산 계정이 만들어지지 않았다")
                    .isNotNull();
            BigDecimal issuerBalance = walletRepository
                    .findKrwWalletByUserId(settledIssuer.getUser().getId())
                    .orElseThrow().getBalance();
            softly.assertThat(issuerBalance.compareTo(settled.getUndistributedAmount()))
                    .as("발행사 지갑 잔고(%s)가 미분배 잔액(%s)과 다르다",
                            issuerBalance, settled.getUndistributedAmount())
                    .isZero();
        });
    }
}
