package com.tokit.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookDtoCacheTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("OrderBookDto 직렬화 및 역직렬화: Redis 및 WebSocket 전송용 오더북 JSON 변환 정합성을 검증한다.")
    void serializeAndDeserialize_OrderBookDto_Success() throws Exception {
        // Given
        OrderBookDto.OrderBookEntry bid = new OrderBookDto.OrderBookEntry(
                new BigDecimal("150000"), new BigDecimal("100.0")
        );
        OrderBookDto.OrderBookEntry ask = new OrderBookDto.OrderBookEntry(
                new BigDecimal("151000"), new BigDecimal("50.0")
        );

        OrderBookDto originalDto = new OrderBookDto(
                "APPL-STO",
                List.of(bid),
                List.of(ask)
        );

        // When
        String json = objectMapper.writeValueAsString(originalDto);
        OrderBookDto deserializedDto = objectMapper.readValue(json, OrderBookDto.class);

        // Then
        assertThat(deserializedDto).isNotNull();
        assertThat(deserializedDto.symbol()).isEqualTo("APPL-STO");
        assertThat(deserializedDto.bids()).hasSize(1);
        assertThat(deserializedDto.bids().get(0).price()).isEqualTo(new BigDecimal("150000"));
        assertThat(deserializedDto.asks()).hasSize(1);
        assertThat(deserializedDto.asks().get(0).price()).isEqualTo(new BigDecimal("151000"));
    }
}
