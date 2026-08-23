package com.tokit.domain.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    @DisplayName("OrderStatus 열거형 검증: OPEN, PARTIAL, FILLED, CANCELED 주문 상태가 정상 정의되어 있다.")
    void orderStatusValues_MatchExpectedEnums() {
        // Given & When & Then
        assertThat(OrderStatus.valueOf("OPEN")).isEqualTo(OrderStatus.OPEN);
        assertThat(OrderStatus.valueOf("PARTIAL")).isEqualTo(OrderStatus.PARTIAL);
        assertThat(OrderStatus.valueOf("FILLED")).isEqualTo(OrderStatus.FILLED);
        assertThat(OrderStatus.valueOf("CANCELED")).isEqualTo(OrderStatus.CANCELED);

        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.OPEN, OrderStatus.PARTIAL, OrderStatus.FILLED, OrderStatus.CANCELED
        );
    }
}
