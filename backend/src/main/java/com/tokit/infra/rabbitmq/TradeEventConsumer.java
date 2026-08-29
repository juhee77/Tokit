package com.tokit.infra.rabbitmq;

import com.tokit.global.config.RabbitMQConfig;
import com.tokit.global.observability.TradingMetrics;
import com.tokit.infra.alert.SlackAlertService;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {

    private final ContractService contractService;
    private final SlackAlertService slackAlertService;
    private final TradingMetrics tradingMetrics;

    @RabbitListener(queues = RabbitMQConfig.TRADE_QUEUE_NAME)
    public void consumeTradeEvent(TradeEvent event) {
        log.info("Consumed trade event from RabbitMQ: {}", event);
        try {
            // 온체인 이체 처리 실행 (Force Transfer). 일시적 실패는 ContractService가 재시도합니다.
            contractService.handleTransferByPartition(
                    event.assetSymbol(),
                    "DEFAULT",
                    event.sellerWalletAddress(),
                    event.buyerWalletAddress(),
                    event.quantity()
            );
            log.info("Successfully completed on-chain settlement for trade id: {}", event.tradeId());
        } catch (Exception e) {
            // 체결 자체는 오프체인 원장에 이미 확정되어 있으므로 예외를 삼켜 매칭 파이프라인을 지킵니다.
            // 다만 온체인 잔고가 오프체인과 어긋난 상태이므로 반드시 사람이 인지해야 합니다.
            // (일 1회 대사 배치가 동일한 불일치를 다시 잡아냅니다.)
            log.error("On-chain settlement failed after retries for trade id: {}", event.tradeId(), e);
            tradingMetrics.recordOnchainSettlementFailure();
            slackAlertService.sendAlert(
                    "온체인 결제 실패",
                    String.format(
                            "체결 ID %d의 온체인 이체가 재시도 후에도 실패했습니다. "
                                    + "종목: %s, 수량: %s, 매도자: %s, 매수자: %s, 원인: %s%n"
                                    + "오프체인 잔고는 체결 상태이므로 온체인 정합성 수동 확인이 필요합니다.",
                            event.tradeId(), event.assetSymbol(), event.quantity(),
                            event.sellerWalletAddress(), event.buyerWalletAddress(), e.getMessage())
            );
        }
    }
}
