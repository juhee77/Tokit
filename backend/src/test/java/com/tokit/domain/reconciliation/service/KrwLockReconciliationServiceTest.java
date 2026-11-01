package com.tokit.domain.reconciliation.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.fee.service.FeePolicy;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.reconciliation.dto.KrwLockMismatch;
import com.tokit.domain.reconciliation.dto.KrwLockSettlementReport;
import com.tokit.domain.reconciliation.entity.KrwLockSettlement;
import com.tokit.domain.reconciliation.repository.KrwLockSettlementRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매수 홀딩 잔여 정산 (#27).
 *
 * <p>기대 불변식: KRW 지갑의 lockedBalance = Σ(활성 매수 주문의 price * remainQty + 수수료).
 * 테스트 트랜잭션 안에서 실행하므로 정산 결과는 모두 롤백된다.
 */
@SpringBootTest
@Transactional
class KrwLockReconciliationServiceTest {

    private static final Long ADMIN_ID = 999L;

    @Autowired private KrwLockReconciliationService service;
    @Autowired private KrwLockSettlementRepository settlementRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;
    @Autowired private FeePolicy feePolicy;

    private Asset asset;
    private String uid;

    @BeforeEach
    void setUp() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("KrwLock Test Issuer")
                .bizRegNo("krwlock-" + uid)
                .build());
        asset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("KrwLock Test Asset")
                .symbol("KLK-" + uid)
                .totalSupply(BigDecimal.valueOf(1000))
                .issuePrice(BigDecimal.valueOf(10000))
                .status("거래중")
                .contractAddress("0xKrwLockTest")
                .build());
    }

    @Test
    @DisplayName("홀딩이 활성 매수 주문 잔량 홀딩액과 같은 지갑은 드라이런에도 나오지 않고 정산해도 바뀌지 않는다.")
    void consistentWalletIsUntouched() {
        User user = newUser("ok");
        // 3주 중 1주가 남은 부분 체결 주문 + 아직 체결되지 않은 주문
        buyOrder(user, 10000, 3, 1, OrderStatus.PARTIAL);
        buyOrder(user, 12345, 2, 2, OrderStatus.OPEN);
        BigDecimal expected = hold(10000, 1).add(hold(12345, 2));
        Wallet wallet = krwWallet(user, BigDecimal.valueOf(100_000), expected);

        assertThat(service.preview()).noneMatch(m -> m.userId().equals(user.getId()));
        assertThat(service.settle(user.getId(), ADMIN_ID)).isEmpty();

        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("100000");
        assertThat(after.getLockedBalance()).isEqualByComparingTo(expected);
        assertThat(settlementRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("잔여가 묶인 지갑은 정확히 그 차액만 balance로 환급되고 정산 내역이 남는다.")
    void residueIsRefundedExactly() {
        User user = newUser("residue");
        buyOrder(user, 10000, 10, 4, OrderStatus.PARTIAL);
        // 체결 완료·취소된 주문은 더 이상 홀딩 대상이 아니다.
        buyOrder(user, 9000, 5, 0, OrderStatus.FILLED);
        buyOrder(user, 8000, 5, 5, OrderStatus.CANCELED);
        // 매도 주문은 KRW가 아니라 자산 지갑을 홀딩한다.
        order(user, OrderType.SELL, 11000, 3, 3, OrderStatus.OPEN);

        BigDecimal expected = hold(10000, 4);            // 40,040
        BigDecimal residue = new BigDecimal("1503.5000"); // 가격 개선 차액 + 수수료 절사 잔여
        Wallet wallet = krwWallet(user, BigDecimal.valueOf(50_000), expected.add(residue));

        KrwLockMismatch previewed = service.preview().stream()
                .filter(m -> m.userId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(previewed.action()).isEqualTo(KrwLockMismatch.Action.REFUND);
        assertThat(previewed.expectedLocked()).isEqualByComparingTo(expected);
        assertThat(previewed.difference()).isEqualByComparingTo(residue);
        // 드라이런은 아무것도 바꾸지 않는다.
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("50000");

        Optional<KrwLockMismatch> settled = service.settle(user.getId(), ADMIN_ID);
        assertThat(settled).get().extracting(KrwLockMismatch::action).isEqualTo(KrwLockMismatch.Action.REFUND);

        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("51503.5");
        assertThat(after.getLockedBalance()).isEqualByComparingTo(expected);

        List<KrwLockSettlement> records = settlementRepository.findByUserId(user.getId());
        assertThat(records).hasSize(1);
        KrwLockSettlement record = records.get(0);
        assertThat(record.getWalletId()).isEqualTo(wallet.getId());
        assertThat(record.getLockedBalanceBefore()).isEqualByComparingTo(expected.add(residue));
        assertThat(record.getExpectedLocked()).isEqualByComparingTo(expected);
        assertThat(record.getRefundAmount()).isEqualByComparingTo(residue);
        assertThat(record.getBalanceBefore()).isEqualByComparingTo("50000");
        assertThat(record.getBalanceAfter()).isEqualByComparingTo("51503.5");
        assertThat(record.getSettledBy()).isEqualTo(ADMIN_ID);

        // 다시 돌려도 이중 환급되지 않는다.
        assertThat(service.settle(user.getId(), ADMIN_ID)).isEmpty();
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("51503.5");
        assertThat(settlementRepository.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    @DisplayName("활성 매수 주문이 하나도 없는데 홀딩이 남은 지갑은 전액 환급된다.")
    void residueWithoutActiveOrdersIsFullyRefunded() {
        User user = newUser("noorder");
        buyOrder(user, 10000, 3, 0, OrderStatus.FILLED);
        Wallet wallet = krwWallet(user, BigDecimal.ZERO, BigDecimal.valueOf(7));

        service.settle(user.getId(), ADMIN_ID);

        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("7");
        assertThat(after.getLockedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("홀딩이 모자란(음수 차이) 지갑은 보고만 하고 정산하지 않는다.")
    void shortfallIsReportedButSkipped() {
        User user = newUser("short");
        buyOrder(user, 10000, 1, 1, OrderStatus.OPEN);
        Wallet wallet = krwWallet(user, BigDecimal.valueOf(5_000), BigDecimal.valueOf(10_000)); // 필요액 10,010

        KrwLockMismatch previewed = service.preview().stream()
                .filter(m -> m.userId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(previewed.action()).isEqualTo(KrwLockMismatch.Action.SKIP_SHORTFALL);
        assertThat(previewed.difference()).isEqualByComparingTo("-10");

        Optional<KrwLockMismatch> settled = service.settle(user.getId(), ADMIN_ID);
        assertThat(settled).get().extracting(KrwLockMismatch::action).isEqualTo(KrwLockMismatch.Action.SKIP_SHORTFALL);

        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualByComparingTo("5000");
        assertThat(after.getLockedBalance()).isEqualByComparingTo("10000");
        assertThat(settlementRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("일괄 정산 보고서는 환급과 건너뜀을 나눠 담고, 정상 지갑은 넣지 않는다.")
    void reportSeparatesRefundedAndSkipped() {
        User ok = newUser("r-ok");
        buyOrder(ok, 10000, 1, 1, OrderStatus.OPEN);
        krwWallet(ok, BigDecimal.ZERO, hold(10000, 1));

        User residue = newUser("r-res");
        buyOrder(residue, 10000, 1, 1, OrderStatus.OPEN);
        krwWallet(residue, BigDecimal.ZERO, hold(10000, 1).add(BigDecimal.valueOf(3)));

        User shortfall = newUser("r-short");
        buyOrder(shortfall, 10000, 1, 1, OrderStatus.OPEN);
        krwWallet(shortfall, BigDecimal.ZERO, BigDecimal.ONE);

        KrwLockSettlementReport report = service.settleUsers(
                List.of(ok.getId(), residue.getId(), shortfall.getId()), ADMIN_ID);

        assertThat(report.refunded()).extracting(KrwLockMismatch::userId).containsExactly(residue.getId());
        assertThat(report.skipped()).extracting(KrwLockMismatch::userId).containsExactly(shortfall.getId());
        assertThat(report.totalRefunded()).isEqualByComparingTo("3");
    }

    private BigDecimal hold(long price, long qty) {
        BigDecimal amount = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(qty));
        return amount.add(feePolicy.calculate(amount));
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .name("krwlock-" + tag)
                .email("krwlock-" + tag + "-" + uid + "@test.com")
                .password("{noop}test-password")
                .walletAddress("0xKRWLOCK" + tag + uid)
                .kycStatus(true)
                .build());
    }

    private Wallet krwWallet(User user, BigDecimal balance, BigDecimal locked) {
        return walletRepository.save(Wallet.builder().user(user).asset(null)
                .balance(balance).lockedBalance(locked).build());
    }

    private void buyOrder(User user, long price, long qty, long remain, OrderStatus status) {
        order(user, OrderType.BUY, price, qty, remain, status);
    }

    private void order(User user, OrderType type, long price, long qty, long remain, OrderStatus status) {
        orderRepository.save(Order.builder()
                .user(user)
                .asset(asset)
                .type(type)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .remainQty(BigDecimal.valueOf(remain))
                .status(status)
                .build());
    }
}
