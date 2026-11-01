package com.tokit.domain.reconciliation.service;

import com.tokit.domain.fee.service.FeePolicy;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.reconciliation.dto.KrwLockMismatch;
import com.tokit.domain.reconciliation.dto.KrwLockSettlementReport;
import com.tokit.domain.reconciliation.entity.KrwLockSettlement;
import com.tokit.domain.reconciliation.repository.KrwLockSettlementRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * KRW 지갑에 잘못 묶인 매수 홀딩 잔여를 찾아 돌려주는 일회성 정산 (#27).
 *
 * <p>KRW 지갑의 {@code lockedBalance}를 움직이는 것은 매수 주문뿐이므로, 정상이라면
 * {@code lockedBalance = Σ(활성 매수 주문의 잔량 홀딩액)}이 성립한다. 잔량 홀딩액은 주문 접수·취소와
 * 같은 식({@code price * remainQty + 수수료})으로 계산한다. 체결 로직이 가격 개선 차액과 수수료 절사
 * 잔여를 풀지 않던 동안 이 식보다 많은 금액이 묶였고, 그 차이를 balance로 옮긴다.
 *
 * <p>차이가 음수(홀딩 부족)인 지갑은 돌려줄 금액이 아니라 다른 원인의 결손이므로 정산하지 않고 보고만 한다.
 *
 * <p>지갑마다 별도 트랜잭션으로 처리한다. 한 지갑의 실패가 이미 끝난 정산을 되돌리지 않게 하고,
 * 지갑 락을 오래 쥐지 않기 위해서다.
 */
@Slf4j
@Service
public class KrwLockReconciliationService {

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.OPEN, OrderStatus.PARTIAL);

    private final WalletRepository walletRepository;
    private final OrderRepository orderRepository;
    private final KrwLockSettlementRepository settlementRepository;
    private final FeePolicy feePolicy;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public KrwLockReconciliationService(WalletRepository walletRepository,
                                        OrderRepository orderRepository,
                                        KrwLockSettlementRepository settlementRepository,
                                        FeePolicy feePolicy,
                                        PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
        this.feePolicy = feePolicy;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * 드라이런. 불변식이 깨진 KRW 지갑을 모두 보여 준다. 아무것도 바꾸지 않는다.
     *
     * <p>락 없이 읽으므로 체결이 진행 중이면 순간적인 값이 섞일 수 있다. 실제 정산은
     * {@link #settle(Long, Long)}이 지갑 락을 잡은 뒤 다시 계산한 값으로 한다.
     */
    public List<KrwLockMismatch> preview() {
        return readOnlyTransactionTemplate.execute(status -> {
            Map<Long, BigDecimal> expectedByUser = orderRepository
                    .findByTypeAndStatusIn(OrderType.BUY, ACTIVE_STATUSES).stream()
                    .collect(Collectors.groupingBy(Order::getUserId,
                            Collectors.reducing(BigDecimal.ZERO, this::remainingHold, BigDecimal::add)));

            List<KrwLockMismatch> mismatches = new ArrayList<>();
            for (Wallet wallet : walletRepository.findAllKrwWallets()) {
                Long userId = wallet.getUser().getId();
                toMismatch(userId, wallet, expectedByUser.getOrDefault(userId, BigDecimal.ZERO))
                        .ifPresent(mismatches::add);
            }
            return mismatches;
        });
    }

    /**
     * 드라이런에서 차이가 발견된 지갑을 모두 정산한다.
     *
     * @param adminId 정산을 실행한 관리자. 내역에 남긴다.
     */
    public KrwLockSettlementReport settleAll(Long adminId) {
        return settleUsers(preview().stream().map(KrwLockMismatch::userId).toList(), adminId);
    }

    KrwLockSettlementReport settleUsers(Collection<Long> userIds, Long adminId) {
        List<KrwLockMismatch> refunded = new ArrayList<>();
        List<KrwLockMismatch> skipped = new ArrayList<>();
        for (Long userId : userIds) {
            settle(userId, adminId).ifPresent(result -> {
                if (result.action() == KrwLockMismatch.Action.REFUND) {
                    refunded.add(result);
                } else {
                    skipped.add(result);
                }
            });
        }
        BigDecimal total = refunded.stream().map(KrwLockMismatch::difference).reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("[KrwLockReconciliation] 정산 완료: 환급 {}건, 합계 {}, 홀딩 부족으로 건너뜀 {}건",
                refunded.size(), total, skipped.size());
        return new KrwLockSettlementReport(refunded, skipped, total);
    }

    /**
     * 한 사용자의 KRW 지갑을 비관적 락으로 잡고, 활성 매수 주문을 다시 읽어 차이를 정산한다.
     *
     * <p>주문 접수·취소·체결은 모두 같은 트랜잭션에서 이 지갑 락을 잡고 주문을 바꾸므로,
     * 락을 잡은 뒤 읽은 주문 잔량은 지갑 잔고와 같은 시점의 값이다.
     *
     * @return 차이가 없으면 빈 값. 음수 차이는 {@link KrwLockMismatch.Action#SKIP_SHORTFALL}로 돌려주고 바꾸지 않는다.
     */
    public Optional<KrwLockMismatch> settle(Long userId, Long adminId) {
        return transactionTemplate.execute(status -> {
            Optional<Wallet> found = walletRepository.findKrwWalletByUserIdWithPessimisticLock(userId);
            if (found.isEmpty()) {
                return Optional.<KrwLockMismatch>empty();
            }
            Wallet wallet = found.get();
            BigDecimal expected = orderRepository
                    .findByUser_IdAndTypeAndStatusIn(userId, OrderType.BUY, ACTIVE_STATUSES).stream()
                    .map(this::remainingHold)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Optional<KrwLockMismatch> mismatch = toMismatch(userId, wallet, expected);
            mismatch.filter(m -> m.action() == KrwLockMismatch.Action.REFUND)
                    .ifPresent(m -> refund(wallet, m, adminId));
            mismatch.filter(m -> m.action() == KrwLockMismatch.Action.SKIP_SHORTFALL)
                    .ifPresent(m -> log.warn("[KrwLockReconciliation] 홀딩 부족 지갑 정산 건너뜀: userId={}, locked={}, expected={}",
                            userId, m.lockedBalance(), m.expectedLocked()));
            return mismatch;
        });
    }

    private void refund(Wallet wallet, KrwLockMismatch mismatch, Long adminId) {
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(mismatch.difference());
        wallet.updateBalance(balanceAfter, mismatch.expectedLocked());

        settlementRepository.save(KrwLockSettlement.builder()
                .userId(mismatch.userId())
                .walletId(wallet.getId())
                .lockedBalanceBefore(mismatch.lockedBalance())
                .expectedLocked(mismatch.expectedLocked())
                .refundAmount(mismatch.difference())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .settledBy(adminId)
                .build());
        log.info("[KrwLockReconciliation] 홀딩 잔여 환급: userId={}, amount={}", mismatch.userId(), mismatch.difference());
    }

    private Optional<KrwLockMismatch> toMismatch(Long userId, Wallet wallet, BigDecimal expected) {
        BigDecimal difference = wallet.getLockedBalance().subtract(expected);
        if (difference.signum() == 0) {
            return Optional.empty();
        }
        KrwLockMismatch.Action action = difference.signum() > 0
                ? KrwLockMismatch.Action.REFUND
                : KrwLockMismatch.Action.SKIP_SHORTFALL;
        return Optional.of(new KrwLockMismatch(userId, wallet.getId(), wallet.getBalance(),
                wallet.getLockedBalance(), expected, difference, action));
    }

    /** 매수 주문 잔량에 대해 홀딩되어 있어야 하는 금액. 주문 접수·취소와 같은 식이다. */
    private BigDecimal remainingHold(Order order) {
        BigDecimal amount = order.getPrice().multiply(order.getRemainQty());
        return amount.add(feePolicy.calculate(amount));
    }
}
