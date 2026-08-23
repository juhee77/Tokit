package com.tokit.domain.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTypeTest {

    @Test
    @DisplayName("OrderType 열거형 검증: 매수(BUY) 및 매도(SELL) 주문 구분이 정상 정의되어 있다.")
    void orderTypeValues_MatchBuyAndSellEnums() {
        // Given & When & Then
        assertThat(OrderType.valueOf("BUY")).isEqualTo(OrderType.BUY);
        assertThat(OrderType.valueOf("SELL")).isEqualTo(OrderType.SELL);
        assertThat(OrderType.values()).containsExactly(OrderType.BUY, OrderType.SELL);
    }
}
