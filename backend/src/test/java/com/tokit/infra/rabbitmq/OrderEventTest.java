package com.tokit.infra.rabbitmq;

import com.tokit.domain.order.entity.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventTest {

    @Test
    @DisplayName("OrderEvent 레코드 데이터 검증: RabbitMQ 전송용 비동기 주문 이벤트 메시지 필드가 정상 할당된다.")
    void recordInstantiation_HoldsOrderFieldsCorrectly() {
        // Given & When
        OrderEvent event = new OrderEvent(
                100L, 1L, "GANGNAM-STO", OrderType.BUY, new BigDecimal("10000"), new BigDecimal("5")
        );

        // Then
        assertThat(event.orderId()).isEqualTo(100L);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.assetSymbol()).isEqualTo("GANGNAM-STO");
        assertThat(event.orderType()).isEqualTo(OrderType.BUY);
        assertThat(event.price()).isEqualTo(new BigDecimal("10000"));
        assertThat(event.quantity()).isEqualTo(new BigDecimal("5"));
    }
}
