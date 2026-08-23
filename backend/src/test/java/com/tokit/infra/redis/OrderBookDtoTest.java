package com.tokit.infra.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookDtoTest {

    @Test
    @DisplayName("OrderBookDto 생성 및 필드 호환성: 자산 심볼, 매수 호가 리스트, 매도 호가 리스트가 레코드에 정상 할당된다.")
    void recordInstantiation_HoldsBidsAndAsksCorrectly() {
        // Given
        OrderBookDto.OrderBookEntry bidEntry = new OrderBookDto.OrderBookEntry(
                new BigDecimal("150000"), new BigDecimal("120.5")
        );
        OrderBookDto.OrderBookEntry askEntry = new OrderBookDto.OrderBookEntry(
                new BigDecimal("151000"), new BigDecimal("80.0")
        );

        // When
        OrderBookDto orderBookDto = new OrderBookDto(
                "APPL-STO",
                List.of(bidEntry),
                List.of(askEntry)
        );

        // Then
        assertThat(orderBookDto.symbol()).isEqualTo("APPL-STO");
        assertThat(orderBookDto.bids()).hasSize(1);
        assertThat(orderBookDto.bids().get(0).price()).isEqualTo(new BigDecimal("150000"));
        assertThat(orderBookDto.bids().get(0).quantity()).isEqualTo(new BigDecimal("120.5"));

        assertThat(orderBookDto.asks()).hasSize(1);
        assertThat(orderBookDto.asks().get(0).price()).isEqualTo(new BigDecimal("151000"));
        assertThat(orderBookDto.asks().get(0).quantity()).isEqualTo(new BigDecimal("80.0"));
    }
}
