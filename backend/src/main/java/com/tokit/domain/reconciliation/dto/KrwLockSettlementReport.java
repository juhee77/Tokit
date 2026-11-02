package com.tokit.domain.reconciliation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 매수 홀딩 잔여 정산 실행 결과.
 *
 * @param refunded 환급한 지갑. 정산 시점(락을 잡은 뒤) 다시 계산한 값이다.
 * @param skipped  홀딩이 모자라 정산하지 않은 지갑.
 */
public record KrwLockSettlementReport(
        List<KrwLockMismatch> refunded,
        List<KrwLockMismatch> skipped,
        BigDecimal totalRefunded
) {
}
