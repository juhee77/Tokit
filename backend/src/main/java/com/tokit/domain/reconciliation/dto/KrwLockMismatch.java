package com.tokit.domain.reconciliation.dto;

import java.math.BigDecimal;

/**
 * KRW 지갑의 홀딩 잔고와 활성 매수 주문 잔량 홀딩액 합의 차이.
 *
 * @param difference {@code lockedBalance - expectedLocked}. 양수면 잘못 묶인 금액, 음수면 홀딩 부족.
 */
public record KrwLockMismatch(
        Long userId,
        Long walletId,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal expectedLocked,
        BigDecimal difference,
        Action action
) {
    public enum Action {
        /** 잘못 묶인 금액이므로 balance로 돌려준다. */
        REFUND,
        /** 홀딩이 모자라다. 원인을 따로 확인해야 하므로 정산하지 않고 보고만 한다. */
        SKIP_SHORTFALL
    }
}
