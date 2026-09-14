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

    @Test
    @DisplayName("calculateSpread/calculateTotalBidVolume/calculateTotalAskVolume: 스프레드 및 매수/매도 총 수량이 정확히 계산된다.")
    void calculateMetrics_ReturnsCorrectSpreadAndVolumes() {
        // Given
        OrderBookDto.OrderBookEntry bid1 = new OrderBookDto.OrderBookEntry(new BigDecimal("150000"), new BigDecimal("100"));
        OrderBookDto.OrderBookEntry bid2 = new OrderBookDto.OrderBookEntry(new BigDecimal("149000"), new BigDecimal("50"));

        OrderBookDto.OrderBookEntry ask1 = new OrderBookDto.OrderBookEntry(new BigDecimal("151000"), new BigDecimal("70"));
        OrderBookDto.OrderBookEntry ask2 = new OrderBookDto.OrderBookEntry(new BigDecimal("152000"), new BigDecimal("30"));

        OrderBookDto orderBookDto = new OrderBookDto("APPL-STO", List.of(bid1, bid2), List.of(ask1, ask2));

        // When & Then
        assertThat(orderBookDto.calculateSpread()).isEqualTo(new BigDecimal("1000"));
        assertThat(orderBookDto.calculateTotalBidVolume()).isEqualTo(new BigDecimal("150"));
        assertThat(orderBookDto.calculateTotalAskVolume()).isEqualTo(new BigDecimal("100"));
    }
}
