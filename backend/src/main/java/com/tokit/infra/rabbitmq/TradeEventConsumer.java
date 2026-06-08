package com.tokit.infra.rabbitmq;

import com.tokit.global.config.RabbitMQConfig;
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

    @RabbitListener(queues = RabbitMQConfig.TRADE_QUEUE_NAME)
    public void consumeTradeEvent(TradeEvent event) {
        log.info("Consumed trade event from RabbitMQ: {}", event);
        try {
            // 온체인 이체 처리 실행 (Force Transfer)
            contractService.handleTransferByPartition(
                    event.assetSymbol(),
                    "DEFAULT",
                    event.sellerWalletAddress(),
                    event.buyerWalletAddress(),
                    event.quantity()
            );
            log.info("Successfully completed on-chain settlement for trade id: {}", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to process on-chain settlement for trade id: " + event.tradeId(), e);
        }
    }
}
