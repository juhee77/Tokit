package com.tokit.domain.dividend.service;

import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.dividend.repository.DividendPayoutDetailRepository;
import com.tokit.domain.dividend.repository.DividendPayoutRepository;
import com.tokit.domain.issuer.service.IssuerSettlementAccountService;
import com.tokit.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 배당 집행 결과를 확정하고 미분배 잔액을 발행사에게 반환한다.
 *
 * <p>이 로직을 배치 리스너 안에 두지 않는 이유는 {@code JobExecutionListener.afterJob}이
 * <b>트랜잭션과 영속성 세션 밖에서</b> 실행되기 때문이다. 그 상태에서 {@code payout.getAsset()}
 * 같은 지연 로딩을 건드리면 세션이 없어 실패한다. 정산은 조회·계산·이체가 한 묶음으로
 * 커밋되어야 하므로 별도 트랜잭션 경계를 가진 서비스로 분리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendSettlementService {

    private final DividendPayoutRepository dividendPayoutRepository;
    private final DividendPayoutDetailRepository dividendPayoutDetailRepository;
    private final WalletRepository walletRepository;
    private final IssuerSettlementAccountService issuerSettlementAccountService;

    /**
     * 배치 종료 후 정산을 마감한다.
     *
     * @param jobCompleted 배치가 성공으로 끝났는지. 실패했다면 집행 결과를 기록하지 않는다.
     */
    @Transactional
    public void settle(Long payoutId, boolean jobCompleted) {
        DividendPayout payout = dividendPayoutRepository.findById(payoutId).orElse(null);
        if (payout == null) {
            log.warn("[Dividend Settlement] Payout {} not found", payoutId);
            return;
        }

        if (!jobCompleted) {
            payout.updateStatus("FAILED");
            dividendPayoutRepository.save(payout);
            log.info("[Dividend Settlement] Payout {} marked FAILED", payoutId);
            return;
        }

        // 지급에 성공한 금액만 분배된 것으로 본다. 실패한 내역은 돈이 나가지 않았다.
        BigDecimal distributed = dividendPayoutDetailRepository.findByPayout_Id(payoutId).stream()
                .filter(detail -> "SUCCESS".equals(detail.getStatus()))
                .map(DividendPayoutDetail::getPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal undistributed = payout.getTotalDividendAmount().subtract(distributed);

        payout.recordSettlement(distributed, undistributed);
        returnToIssuer(payout, undistributed);

        payout.updateStatus("COMPLETED");
        dividendPayoutRepository.save(payout);
        log.info("[Dividend Settlement] Payout {} completed. distributed={}, undistributed={}",
                payoutId, distributed, undistributed);
    }

    /**
     * 절사로 남은 잔액을 발행사 원화 지갑으로 되돌린다.
     *
     * <p>이 잔액을 아무 데도 넣지 않으면 재원에서 빠진 채 원장에서 사라진다. 재원을 넣은
     * 주체가 발행사이므로 발행사에게 돌려준다.
     */
    private void returnToIssuer(DividendPayout payout, BigDecimal undistributed) {
        if (undistributed.signum() <= 0) {
            return;
        }
        // 정산 계정이 없으면 이 시점에 만든다. 이 기능 이전에 등록된 발행사도 처리해야 한다.
        Long issuerUserId = issuerSettlementAccountService.ensureKrwWallet(
                payout.getAsset().getIssuer());

        walletRepository.findKrwWalletByUserIdWithPessimisticLock(issuerUserId).ifPresentOrElse(
                issuerWallet -> {
                    issuerWallet.updateBalance(
                            issuerWallet.getBalance().add(undistributed),
                            issuerWallet.getLockedBalance());
                    walletRepository.save(issuerWallet);
                    log.info("[Dividend Settlement] Returned undistributed {} KRW to issuer user {}",
                            undistributed, issuerUserId);
                },
                // 지갑을 보장한 직후이므로 도달하면 안 된다. 도달하면 잔액이 사라지는 것이므로
                // 조용히 넘기지 않는다.
                () -> log.error("[Dividend Settlement] CRITICAL: issuer KRW wallet missing right after "
                                + "provisioning for user {}. Undistributed {} KRW is unaccounted for.",
                        issuerUserId, undistributed));
    }
}
