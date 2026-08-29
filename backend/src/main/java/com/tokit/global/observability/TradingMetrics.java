package com.tokit.global.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 거래 파이프라인의 운영 지표. 체결이 실제로 흐르고 있는지, 온체인 결제가
 * 조용히 실패하고 있지는 않은지를 대시보드/알림에서 판단할 수 있도록 노출합니다.
 */
@Component
public class TradingMetrics {

    private final Counter ordersPlaced;
    private final Counter ordersCanceled;
    private final Counter tradesSettled;
    private final Counter onchainSettlementFailures;
    private final Counter reconciliationMismatches;
    private final Timer matchingDuration;

    public TradingMetrics(MeterRegistry registry) {
        this.ordersPlaced = Counter.builder("tokit.orders.placed")
                .description("접수된 주문 건수")
                .register(registry);
        this.ordersCanceled = Counter.builder("tokit.orders.canceled")
                .description("취소된 주문 건수")
                .register(registry);
        this.tradesSettled = Counter.builder("tokit.trades.settled")
                .description("오프체인 원장에 확정된 체결 건수")
                .register(registry);
        this.onchainSettlementFailures = Counter.builder("tokit.settlement.onchain.failures")
                .description("재시도 후에도 실패한 온체인 결제 건수")
                .register(registry);
        this.reconciliationMismatches = Counter.builder("tokit.reconciliation.mismatches")
                .description("대사 배치가 발견한 온·오프체인 잔고 불일치 건수")
                .register(registry);
        this.matchingDuration = Timer.builder("tokit.matching.duration")
                .description("주문 1건의 매칭 처리 소요 시간")
                .register(registry);
    }

    public void recordOrderPlaced() {
        ordersPlaced.increment();
    }

    public void recordOrderCanceled() {
        ordersCanceled.increment();
    }

    public void recordTradeSettled() {
        tradesSettled.increment();
    }

    public void recordOnchainSettlementFailure() {
        onchainSettlementFailures.increment();
    }

    public void recordReconciliationMismatch(BigDecimal difference) {
        reconciliationMismatches.increment();
    }

    public Timer.Sample startMatchingTimer(MeterRegistry registry) {
        return Timer.start(registry);
    }

    public Timer matchingTimer() {
        return matchingDuration;
    }
}
