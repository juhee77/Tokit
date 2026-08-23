package com.tokit.infra.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookEntryTest {

    @Test
    @DisplayName("OrderBookEntry 레코드 검증: 오더북 단가 및 잔여 수량 필드가 정확히 할당된다.")
    void recordEquality_VerifiesImmutabilityAndFields() {
        // Given & When
        OrderBookDto.OrderBookEntry entry1 = new OrderBookDto.OrderBookEntry(
                new BigDecimal("150000"), new BigDecimal("10.5")
        );
        OrderBookDto.OrderBookEntry entry2 = new OrderBookDto.OrderBookEntry(
                new BigDecimal("150000"), new BigDecimal("10.5")
        );

        // Then
        assertThat(entry1.price()).isEqualTo(new BigDecimal("150000"));
        assertThat(entry1.quantity()).isEqualTo(new BigDecimal("10.5"));
        assertThat(entry1).isEqualTo(entry2);
        assertThat(entry1.hashCode()).isEqualTo(entry2.hashCode());
    }
}
