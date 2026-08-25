package com.tokit.infra.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookDtoWebSocketTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("OrderBookDto STOMP WebSocket 호가창 직렬화 검증: /topic/orderbook/{symbol} 구독용 JSON 메시지 프레임이 정확히 생성된다.")
    void record_SerializationAndAccessors() throws Exception {
        // Given
        OrderBookDto.OrderBookEntry bid1 = new OrderBookDto.OrderBookEntry(new BigDecimal("150000"), new BigDecimal("120.5"));
        OrderBookDto.OrderBookEntry ask1 = new OrderBookDto.OrderBookEntry(new BigDecimal("151000"), new BigDecimal("80.0"));

        OrderBookDto dto = new OrderBookDto("GNPM", List.of(bid1), List.of(ask1));

        // When
        String json = objectMapper.writeValueAsString(dto);

        // Then
        assertThat(dto.symbol()).isEqualTo("GNPM");
        assertThat(dto.bids()).hasSize(1);
        assertThat(dto.asks()).hasSize(1);
        assertThat(json).contains("\"symbol\":\"GNPM\"");
        assertThat(json).contains("\"bids\":[{\"price\":150000");
        assertThat(json).contains("\"asks\":[{\"price\":151000");
    }
}
