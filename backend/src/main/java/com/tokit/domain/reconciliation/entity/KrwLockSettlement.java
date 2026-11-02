package com.tokit.domain.reconciliation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KRW 지갑에 잘못 묶여 있던 매수 홀딩 잔여를 balance로 돌려준 정산 내역 (#27).
 */
@Entity
@Table(name = "krw_lock_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KrwLockSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "locked_balance_before", nullable = false, precision = 20, scale = 4)
    private BigDecimal lockedBalanceBefore;

    @Column(name = "expected_locked", nullable = false, precision = 20, scale = 4)
    private BigDecimal expectedLocked;

    @Column(name = "refund_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal refundAmount;

    @Column(name = "balance_before", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    @Builder
    public KrwLockSettlement(Long userId, Long walletId, BigDecimal lockedBalanceBefore, BigDecimal expectedLocked,
                             BigDecimal refundAmount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                             Long settledBy, LocalDateTime settledAt) {
        this.userId = userId;
        this.walletId = walletId;
        this.lockedBalanceBefore = lockedBalanceBefore;
        this.expectedLocked = expectedLocked;
        this.refundAmount = refundAmount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.settledBy = settledBy;
        this.settledAt = settledAt != null ? settledAt : LocalDateTime.now();
    }
}
