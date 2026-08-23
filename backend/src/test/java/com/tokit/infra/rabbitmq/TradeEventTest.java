package com.tokit.infra.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TradeEventTest {

    @Test
    @DisplayName("TradeEvent 레코드 데이터 검증: RabbitMQ 체결 완료 비동기 이벤트 메시지 레코드 필드가 정상 할당된다.")
    void recordInstantiation_HoldsTradeFieldsCorrectly() {
        // Given & When
        TradeEvent event = new TradeEvent(
                999L, 100L, 200L, "BUSAN-STO", "0xBUYER_ADDR", "0xSELLER_ADDR", new BigDecimal("15000"), new BigDecimal("10")
        );

        // Then
        assertThat(event.tradeId()).isEqualTo(999L);
        assertThat(event.buyOrderId()).isEqualTo(100L);
        assertThat(event.sellOrderId()).isEqualTo(200L);
        assertThat(event.assetSymbol()).isEqualTo("BUSAN-STO");
        assertThat(event.buyerWalletAddress()).isEqualTo("0xBUYER_ADDR");
        assertThat(event.sellerWalletAddress()).isEqualTo("0xSELLER_ADDR");
        assertThat(event.price()).isEqualTo(new BigDecimal("15000"));
        assertThat(event.quantity()).isEqualTo(new BigDecimal("10"));
    }
}
